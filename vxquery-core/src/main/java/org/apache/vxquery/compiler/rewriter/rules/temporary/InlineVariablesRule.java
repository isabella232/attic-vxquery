/*
 * Copyright 2009-2013 by The Regents of the University of California
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * you may obtain a copy of the License from
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.vxquery.compiler.rewriter.rules.temporary;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.mutable.Mutable;

import edu.uci.ics.hyracks.algebricks.common.exceptions.AlgebricksException;
import edu.uci.ics.hyracks.algebricks.core.algebra.base.ILogicalExpression;
import edu.uci.ics.hyracks.algebricks.core.algebra.base.ILogicalOperator;
import edu.uci.ics.hyracks.algebricks.core.algebra.base.IOptimizationContext;
import edu.uci.ics.hyracks.algebricks.core.algebra.base.LogicalOperatorTag;
import edu.uci.ics.hyracks.algebricks.core.algebra.base.LogicalVariable;
import edu.uci.ics.hyracks.algebricks.core.algebra.expressions.AbstractFunctionCallExpression;
import edu.uci.ics.hyracks.algebricks.core.algebra.expressions.AbstractLogicalExpression;
import edu.uci.ics.hyracks.algebricks.core.algebra.expressions.VariableReferenceExpression;
import edu.uci.ics.hyracks.algebricks.core.algebra.functions.FunctionIdentifier;
import edu.uci.ics.hyracks.algebricks.core.algebra.operators.logical.AbstractLogicalOperator;
import edu.uci.ics.hyracks.algebricks.core.algebra.operators.logical.AssignOperator;
import edu.uci.ics.hyracks.algebricks.core.algebra.operators.logical.visitors.VariableUtilities;
import edu.uci.ics.hyracks.algebricks.core.algebra.visitors.ILogicalExpressionReferenceTransform;
import edu.uci.ics.hyracks.algebricks.core.rewriter.base.IAlgebraicRewriteRule;

/**
 * Replaces variable reference expressions with their assigned function-call expression where applicable
 * (some variables are generated by datasources).
 * Inlining variables may enable other optimizations by allowing selects and assigns to be moved
 * (e.g., a select may be pushed into a join to enable an efficient physical join operator).
 * 
 * Preconditions/Assumptions:
 * Assumes no projects are in the plan. Only inlines variables whose assigned expression is a function call 
 * (i.e., this rule ignores right-hand side constants and other variable references expressions  
 * 
 * Postconditions/Examples:
 * All qualifying variables have been inlined.
 * 
 * Example (simplified):
 * 
 * Before plan:
 * select <- [$$1 < $$2 + $$0]
 *   assign [$$2] <- [funcZ() + $$0]
 *     assign [$$0, $$1] <- [funcX(), funcY()]
 * 
 * After plan:
 * select <- [funcY() < funcZ() + funcX() + funcX()]
 *   assign [$$2] <- [funcZ() + funcX()]
 *     assign [$$0, $$1] <- [funcX(), funcY()]
 */
public class InlineVariablesRule implements IAlgebraicRewriteRule {

    // Map of variables that could be replaced by their producing expression.
    // Populated during the top-down sweep of the plan.
    protected Map<LogicalVariable, ILogicalExpression> varAssignRhs = new HashMap<LogicalVariable, ILogicalExpression>();

    // Visitor for replacing variable reference expressions with their originating expression.
    protected InlineVariablesVisitor inlineVisitor = new InlineVariablesVisitor(varAssignRhs);

    // Set of FunctionIdentifiers that we should not inline.
    protected Set<FunctionIdentifier> doNotInlineFuncs = new HashSet<FunctionIdentifier>();

    protected boolean hasRun = false;
    
    protected IInlineVariablePolicy policy;

    public InlineVariablesRule() {
        this.policy = new InlineVariablePolicy();
    }

    public InlineVariablesRule(IInlineVariablePolicy policy) {
        this.policy = policy;
    }
    
    @Override
    public boolean rewritePost(Mutable<ILogicalOperator> opRef, IOptimizationContext context) {
        return false;
    }

    @Override
    public boolean rewritePre(Mutable<ILogicalOperator> opRef, IOptimizationContext context) throws AlgebricksException {
        if (hasRun) {
            return false;
        }
        if (context.checkIfInDontApplySet(this, opRef.getValue())) {
            return false;
        }
        prepare(context);
        boolean modified = inlineVariables(opRef, context);
        if (performFinalAction()) {
            modified = true;
        }
        hasRun = true;
        return modified;
    }

    protected void prepare(IOptimizationContext context) {
        varAssignRhs.clear();
        inlineVisitor.setContext(context);
    }

    protected boolean performBottomUpAction(AbstractLogicalOperator op) throws AlgebricksException {
        if (policy.transformOperator(op)) {
            inlineVisitor.setOperator(op);
            return op.acceptExpressionTransform(inlineVisitor);
        }
        return false;
    }

    protected boolean performFinalAction() throws AlgebricksException {
        return false;
    }

    protected boolean inlineVariables(Mutable<ILogicalOperator> opRef, IOptimizationContext context)
            throws AlgebricksException {
        AbstractLogicalOperator op = (AbstractLogicalOperator) opRef.getValue();

        // Update mapping from variables to expressions during top-down traversal.
        if (op.getOperatorTag() == LogicalOperatorTag.ASSIGN) {
            AssignOperator assignOp = (AssignOperator) op;
            List<LogicalVariable> vars = assignOp.getVariables();
            List<Mutable<ILogicalExpression>> exprs = assignOp.getExpressions();
            for (int i = 0; i < vars.size(); i++) {
                ILogicalExpression expr = exprs.get(i).getValue();
                if (policy.addExpressionToInlineMap(expr, doNotInlineFuncs)) {
                    varAssignRhs.put(vars.get(i), exprs.get(i).getValue());
                }
            }
        }

        boolean modified = false;
        // Follow all operators from this operator.
        for (Mutable<ILogicalOperator> inputOpRef : policy.descendIntoNextOperator(op)) {
            if (inlineVariables(inputOpRef, context)) {
                modified = true;
            }
        }

        if (performBottomUpAction(op)) {
            modified = true;
        }

        if (modified) {
            context.computeAndSetTypeEnvironmentForOperator(op);
            context.addToDontApplySet(this, op);
            // Re-enable rules that we may have already tried. They could be applicable now after inlining.
            context.removeFromAlreadyCompared(opRef.getValue());
        }

        return modified;
    }

    protected class InlineVariablesVisitor implements ILogicalExpressionReferenceTransform {

        private final Map<LogicalVariable, ILogicalExpression> varAssignRhs;
        private final Set<LogicalVariable> liveVars = new HashSet<LogicalVariable>();
        private final List<LogicalVariable> rhsUsedVars = new ArrayList<LogicalVariable>();
        private ILogicalOperator op;
        private IOptimizationContext context;
        // If set, only replace this variable reference.
        private LogicalVariable targetVar;

        public InlineVariablesVisitor(Map<LogicalVariable, ILogicalExpression> varAssignRhs) {
            this.varAssignRhs = varAssignRhs;
        }

        public void setTargetVariable(LogicalVariable targetVar) {
            this.targetVar = targetVar;
        }

        public void setContext(IOptimizationContext context) {
            this.context = context;
        }

        public void setOperator(ILogicalOperator op) throws AlgebricksException {
            this.op = op;
            liveVars.clear();
        }

        @Override
        public boolean transform(Mutable<ILogicalExpression> exprRef) throws AlgebricksException {            
            ILogicalExpression e = exprRef.getValue();
            switch (((AbstractLogicalExpression) e).getExpressionTag()) {
                case VARIABLE: {
                    LogicalVariable var = ((VariableReferenceExpression) e).getVariableReference();
                    // Restrict replacement to targetVar if it has been set.
                    if (targetVar != null && var != targetVar) {
                        return false;
                    }
                    // Make sure has not been excluded from inlining.
                    if (context.shouldNotBeInlined(var)) {
                        return false;
                    }
                    ILogicalExpression rhs = varAssignRhs.get(var);
                    if (rhs == null) {
                        // Variable was not produced by an assign.
                        return false;
                    }

                    // Make sure used variables from rhs are live.
                    if (liveVars.isEmpty()) {
                        VariableUtilities.getLiveVariables(op, liveVars);
                    }
                    rhsUsedVars.clear();
                    rhs.getUsedVariables(rhsUsedVars);
                    for (LogicalVariable rhsUsedVar : rhsUsedVars) {
                        if (!liveVars.contains(rhsUsedVar)) {
                            return false;
                        }
                    }

                    // Replace variable reference with a clone of the rhs expr.
                    exprRef.setValue(rhs.cloneExpression());
                    return true;
                }
                case FUNCTION_CALL: {
                    AbstractFunctionCallExpression fce = (AbstractFunctionCallExpression) e;
                    boolean modified = false;
                    for (Mutable<ILogicalExpression> arg : fce.getArguments()) {
                        if (transform(arg)) {
                            modified = true;
                        }
                    }
                    return modified;
                }
                default: {
                    return false;
                }
            }
        }
    }
    
    public static interface IInlineVariablePolicy {

        public boolean addExpressionToInlineMap(ILogicalExpression expr, Set<FunctionIdentifier> doNotInlineFuncs);

        public List<Mutable<ILogicalOperator>> descendIntoNextOperator(AbstractLogicalOperator op);

        public boolean transformOperator(AbstractLogicalOperator op);

    }

}
