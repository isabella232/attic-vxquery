/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.vxquery.compiler.rewriter;

import java.util.LinkedList;
import java.util.List;

import org.apache.vxquery.compiler.rewriter.rules.ConsolidateAssignAggregateRule;
import org.apache.vxquery.compiler.rewriter.rules.ConvertAssignSortDistinctNodesToOperatorsRule;
import org.apache.vxquery.compiler.rewriter.rules.ConvertAssignToAggregateRule;
import org.apache.vxquery.compiler.rewriter.rules.EliminateSubplanForSinglePathsRule;
import org.apache.vxquery.compiler.rewriter.rules.EliminateUnnestAggregateSubplanRule;
import org.apache.vxquery.compiler.rewriter.rules.InlineReferenceVariablePolicy;
import org.apache.vxquery.compiler.rewriter.rules.IntroduceCollectionRule;
import org.apache.vxquery.compiler.rewriter.rules.IntroduceTwoStepAggregateRule;
import org.apache.vxquery.compiler.rewriter.rules.RemoveUnusedSortDistinctNodesRule;
import org.apache.vxquery.compiler.rewriter.rules.SetCollectionDataSourceRule;
import org.apache.vxquery.compiler.rewriter.rules.SetVariableIdContextRule;
import org.apache.vxquery.compiler.rewriter.rules.temporary.InlineVariablesRule;

import edu.uci.ics.hyracks.algebricks.core.rewriter.base.HeuristicOptimizer;
import edu.uci.ics.hyracks.algebricks.core.rewriter.base.IAlgebraicRewriteRule;
import edu.uci.ics.hyracks.algebricks.rewriter.rules.BreakSelectIntoConjunctsRule;
import edu.uci.ics.hyracks.algebricks.rewriter.rules.ComplexJoinInferenceRule;
import edu.uci.ics.hyracks.algebricks.rewriter.rules.ConsolidateAssignsRule;
import edu.uci.ics.hyracks.algebricks.rewriter.rules.ConsolidateSelectsRule;
import edu.uci.ics.hyracks.algebricks.rewriter.rules.EliminateSubplanRule;
import edu.uci.ics.hyracks.algebricks.rewriter.rules.EnforceStructuralPropertiesRule;
import edu.uci.ics.hyracks.algebricks.rewriter.rules.ExtractCommonOperatorsRule;
import edu.uci.ics.hyracks.algebricks.rewriter.rules.ExtractGbyExpressionsRule;
import edu.uci.ics.hyracks.algebricks.rewriter.rules.FactorRedundantGroupAndDecorVarsRule;
import edu.uci.ics.hyracks.algebricks.rewriter.rules.InferTypesRule;
import edu.uci.ics.hyracks.algebricks.rewriter.rules.InlineAssignIntoAggregateRule;
import edu.uci.ics.hyracks.algebricks.rewriter.rules.IntroduceAggregateCombinerRule;
import edu.uci.ics.hyracks.algebricks.rewriter.rules.IntroduceGroupByCombinerRule;
import edu.uci.ics.hyracks.algebricks.rewriter.rules.IsolateHyracksOperatorsRule;
import edu.uci.ics.hyracks.algebricks.rewriter.rules.PullSelectOutOfEqJoin;
import edu.uci.ics.hyracks.algebricks.rewriter.rules.PushLimitDownRule;
import edu.uci.ics.hyracks.algebricks.rewriter.rules.PushProjectDownRule;
import edu.uci.ics.hyracks.algebricks.rewriter.rules.PushProjectIntoDataSourceScanRule;
import edu.uci.ics.hyracks.algebricks.rewriter.rules.PushSelectDownRule;
import edu.uci.ics.hyracks.algebricks.rewriter.rules.PushSelectIntoJoinRule;
import edu.uci.ics.hyracks.algebricks.rewriter.rules.ReinferAllTypesRule;
import edu.uci.ics.hyracks.algebricks.rewriter.rules.RemoveUnusedAssignAndAggregateRule;
import edu.uci.ics.hyracks.algebricks.rewriter.rules.SetAlgebricksPhysicalOperatorsRule;
import edu.uci.ics.hyracks.algebricks.rewriter.rules.SetExecutionModeRule;

public class RewriteRuleset {
    public final static List<IAlgebraicRewriteRule> buildXQueryNormalizationRuleCollection() {
        List<IAlgebraicRewriteRule> normalization = new LinkedList<IAlgebraicRewriteRule>();
        normalization.add(new SetVariableIdContextRule());

        normalization.add(new RemoveUnusedSortDistinctNodesRule());
        normalization.add(new InlineVariablesRule(new InlineReferenceVariablePolicy()));
        normalization.add(new RemoveUnusedAssignAndAggregateRule());
        // TODO Fix the group by operator before putting back in the rule set.
//        normalization.add(new ConvertAssignSortDistinctNodesToOperatorsRule());

        normalization.add(new ConsolidateAssignAggregateRule());
        normalization.add(new InlineVariablesRule(new InlineReferenceVariablePolicy()));
        normalization.add(new RemoveUnusedAssignAndAggregateRule());
        
        normalization.add(new ConvertAssignToAggregateRule());

        normalization.add(new EliminateUnnestAggregateSubplanRule());
        normalization.add(new InlineVariablesRule(new InlineReferenceVariablePolicy()));
        normalization.add(new RemoveUnusedAssignAndAggregateRule());
        
        normalization.add(new EliminateSubplanForSinglePathsRule());

        normalization.add(new SetCollectionDataSourceRule());
        normalization.add(new IntroduceCollectionRule());
        normalization.add(new RemoveUnusedAssignAndAggregateRule());

        normalization.add(new IntroduceTwoStepAggregateRule());
        return normalization;
    }

    public final static List<IAlgebraicRewriteRule> buildTypeInferenceRuleCollection() {
        List<IAlgebraicRewriteRule> typeInfer = new LinkedList<IAlgebraicRewriteRule>();
        typeInfer.add(new InferTypesRule());
        return typeInfer;
    }

    public final static List<IAlgebraicRewriteRule> buildNormalizationRuleCollection() {
        List<IAlgebraicRewriteRule> normalization = new LinkedList<IAlgebraicRewriteRule>();
        normalization.add(new EliminateSubplanRule());
        normalization.add(new BreakSelectIntoConjunctsRule());
        normalization.add(new PushSelectIntoJoinRule());
        normalization.add(new ExtractGbyExpressionsRule());
        return normalization;
    }

    public final static List<IAlgebraicRewriteRule> buildCondPushDownRuleCollection() {
        List<IAlgebraicRewriteRule> condPushDown = new LinkedList<IAlgebraicRewriteRule>();
        condPushDown.add(new PushSelectDownRule());
        condPushDown.add(new InlineVariablesRule());
        condPushDown.add(new FactorRedundantGroupAndDecorVarsRule());
        condPushDown.add(new EliminateSubplanRule());
        return condPushDown;
    }

    public final static List<IAlgebraicRewriteRule> buildJoinInferenceRuleCollection() {
        List<IAlgebraicRewriteRule> joinInference = new LinkedList<IAlgebraicRewriteRule>();
        joinInference.add(new InlineVariablesRule());
        joinInference.add(new ComplexJoinInferenceRule());
        return joinInference;
    }

    public final static List<IAlgebraicRewriteRule> buildOpPushDownRuleCollection() {
        List<IAlgebraicRewriteRule> opPushDown = new LinkedList<IAlgebraicRewriteRule>();
        opPushDown.add(new PushProjectDownRule());
        opPushDown.add(new PushSelectDownRule());
        return opPushDown;
    }

    public final static List<IAlgebraicRewriteRule> buildDataExchangeRuleCollection() {
        List<IAlgebraicRewriteRule> dataExchange = new LinkedList<IAlgebraicRewriteRule>();
        dataExchange.add(new SetExecutionModeRule());
        return dataExchange;
    }

    public final static List<IAlgebraicRewriteRule> buildConsolidationRuleCollection() {
        List<IAlgebraicRewriteRule> consolidation = new LinkedList<IAlgebraicRewriteRule>();
        consolidation.add(new ConsolidateSelectsRule());
        consolidation.add(new ConsolidateAssignsRule());
        consolidation.add(new InlineAssignIntoAggregateRule());
        consolidation.add(new IntroduceGroupByCombinerRule());
        consolidation.add(new IntroduceAggregateCombinerRule());
        consolidation.add(new RemoveUnusedAssignAndAggregateRule());
        return consolidation;
    }

    public final static List<IAlgebraicRewriteRule> buildPhysicalRewritesAllLevelsRuleCollection() {
        List<IAlgebraicRewriteRule> physicalPlanRewrites = new LinkedList<IAlgebraicRewriteRule>();
        physicalPlanRewrites.add(new PullSelectOutOfEqJoin());
        physicalPlanRewrites.add(new SetAlgebricksPhysicalOperatorsRule());
        physicalPlanRewrites.add(new EnforceStructuralPropertiesRule());
        physicalPlanRewrites.add(new PushProjectDownRule());
        physicalPlanRewrites.add(new PushLimitDownRule());
        return physicalPlanRewrites;
    }

    public final static List<IAlgebraicRewriteRule> buildPhysicalRewritesTopLevelRuleCollection() {
        List<IAlgebraicRewriteRule> physicalPlanRewrites = new LinkedList<IAlgebraicRewriteRule>();
        physicalPlanRewrites.add(new PushLimitDownRule());
        return physicalPlanRewrites;
    }

    public final static List<IAlgebraicRewriteRule> prepareForJobGenRuleCollection() {
        List<IAlgebraicRewriteRule> prepareForJobGenRewrites = new LinkedList<IAlgebraicRewriteRule>();
        prepareForJobGenRewrites.add(new IsolateHyracksOperatorsRule(
                HeuristicOptimizer.hyraxOperatorsBelowWhichJobGenIsDisabled));
        prepareForJobGenRewrites.add(new ExtractCommonOperatorsRule());
        // Re-infer all types, so that, e.g., the effect of not-is-null is
        // propagated.
        prepareForJobGenRewrites.add(new PushProjectIntoDataSourceScanRule());
        prepareForJobGenRewrites.add(new ReinferAllTypesRule());
        return prepareForJobGenRewrites;
    }
}