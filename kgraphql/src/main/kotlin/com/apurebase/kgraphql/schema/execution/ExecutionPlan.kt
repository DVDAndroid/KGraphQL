package com.apurebase.kgraphql.schema.execution

import com.apurebase.kgraphql.schema.model.ast.DefinitionNode
import kotlin.time.Duration

class ExecutionPlan(
        val options: ExecutionOptions,
        val operation: DefinitionNode.ExecutableDefinitionNode.OperationDefinitionNode,
        val operations: List<Execution.Node>,
) : List<Execution.Node> by operations {
    var isSubscription = false
}

data class ExecutionResult(
        val operationInfo: DefinitionNode.ExecutableDefinitionNode.OperationDefinitionNode,
        val duration: Duration,
        val result: String,
)