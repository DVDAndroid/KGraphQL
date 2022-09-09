package com.apurebase.kgraphql.schema.execution

import com.apurebase.kgraphql.schema.model.ast.OperationTypeNode

class ExecutionPlan(
        val options: ExecutionOptions,
        val operation: OperationTypeNode,
        val operations: List<Execution.Node>,
) : List<Execution.Node> by operations {
    var isSubscription = false
}

data class ExecutionResult(
        val executionPlan: ExecutionPlan,
        val millis: Long,
        val result: String,
)