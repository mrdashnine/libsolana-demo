package io.bitrivet.demo

import io.bitrivet.blockchain.solana.core.signer.Signer
import io.bitrivet.blockchain.solana.rpc.RPC

data class RT(
    val config: Config,
    val rpc: RPC,
    val signer: Signer,
)
