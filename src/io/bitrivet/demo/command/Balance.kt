package io.bitrivet.demo.command

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.requireObject
import io.bitrivet.blockchain.solana.spl.token.TokenClient
import io.bitrivet.demo.RT
import kotlinx.coroutines.runBlocking

class Balance: CliktCommand(name = "balance", help = "show SOL and token balances") {
    private val rt by requireObject<RT>()

    override fun run() = runBlocking{
        val solBalance = rt.rpc.getBalance(rt.signer.publicKey)
        println("SOL: ${rt.signer.publicKey}: ${solBalance.balance}")

        val tokenClient = TokenClient(rt.rpc)
        val tokenBalances = tokenClient.getTokenBalancesByOwner(rt.signer.publicKey)
        for (tokenBalance in tokenBalances.values) {
            println("${tokenBalance.token.symbol}: ${tokenBalance.address}: ${tokenBalance.amount}")
        }
    }
}
