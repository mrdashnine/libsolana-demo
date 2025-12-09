package io.bitrivet.demo.command

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import io.bitrivet.blockchain.solana.core.Address
import io.bitrivet.blockchain.solana.core.program.system.program.SystemProgram
import io.bitrivet.blockchain.solana.spl.program.ata.AssociatedTokenAccount
import io.bitrivet.blockchain.solana.spl.program.ata.program.associatedTokenAccount
import io.bitrivet.blockchain.solana.spl.program.token.program.TokenProgram
import io.bitrivet.blockchain.solana.spl.program.token.program.token
import io.bitrivet.blockchain.solana.spl.program.token2022.program.Token2022Program
import io.bitrivet.blockchain.solana.spl.program.token2022.program.token2022
import io.bitrivet.blockchain.solana.spl.token.Token
import io.bitrivet.blockchain.solana.spl.token.TokenBalance
import io.bitrivet.blockchain.solana.spl.token.TokenClient
import io.bitrivet.blockchain.solana.spl.token.decodeTokenAccount
import io.bitrivet.blockchain.solana.spl.token.toRawAmountTruncated
import io.bitrivet.demo.RT
import kotlinx.coroutines.runBlocking
import java.math.BigDecimal

class Transfer : CliktCommand(help = "transfer SOL or SPL tokens") {
    private val source by option("-s", "--source", help = "source token account").convert {
        Address(it.trim())
    }
    private val splToken by option("-t", "--token", help = "source token mint").convert {
        Address(it.trim())
    }
    private val destination by option("-d", "--destination", help = "destination user or token account").convert {
        Address(it.trim())
    }.required()
    private val amount by option("-a", "--amount", help = "amount to transfer").convert { BigDecimal(it) }.required()

    private val rt by requireObject<RT>()

    override fun run() = runBlocking {
        val sourceAccount = getSourceAccount(
            rt,
            source,
            splToken,
        )

        val (destinationAccount, owner) = getDestinationAccount(rt, destination, sourceAccount.token)

        val amountRaw = sourceAccount.token.mint.toRawAmountTruncated(amount)

        val status = rt.rpc.buildAndExecuteTransaction {
            feePayer(rt.signer)
            associatedTokenAccount.createIdempotent(
                rt.signer.publicKey,
                destinationAccount,
                owner,
                sourceAccount.token.mint.address,
                sourceAccount.token.mint.programID,
            )
            when (sourceAccount.token.mint.programID) {
                TokenProgram.programID ->
                    token.transferChecked(
                        sourceAccount.address,
                        sourceAccount.token.mint.address,
                        destinationAccount,
                        rt.signer.publicKey,
                        amount = amountRaw,
                        decimals = sourceAccount.token.mint.decimals,
                    )

                Token2022Program.programID ->
                    token2022.transferChecked(
                        sourceAccount.address,
                        sourceAccount.token.mint.address,
                        destinationAccount,
                        rt.signer.publicKey,
                        amount = amountRaw,
                        decimals = sourceAccount.token.mint.decimals,
                    )
            }
        }

        val statusMessage = if (status.isSuccessful) {
            "success"
        } else {
            "fail: ${status.error}"
        }
        println("transfer: ${status.signature} $statusMessage")
    }

    private suspend fun getSourceAccount(rt: RT, source: Address?, token: Address?): TokenBalance {
        require(source != null || token != null) {
            "Either 'source' or 'token' must be provided"
        }

        val tokenClient = TokenClient(rt.rpc)
        when {
            source != null -> {
                val tokenAccount = tokenClient.getTokenBalanceByAddress(source)
                return tokenAccount ?: throw Exception("Invalid token account $source")
            }

            token != null -> {
                val pdaLegacy = AssociatedTokenAccount.getAssociatedTokenAccountAddress(
                    rt.signer.publicKey,
                    token,
                    TokenProgram.programID,
                )
                val pda2022 = AssociatedTokenAccount.getAssociatedTokenAccountAddress(
                    rt.signer.publicKey,
                    token,
                    Token2022Program.programID,
                )

                val accounts = tokenClient.getTokenBalancesByAddress(listOf(pdaLegacy, pda2022))
                if (accounts.isEmpty()) {
                    throw Exception("No token accounts for mint $token")
                }

                return accounts.values.first()
            }

            else -> throw Exception("unreachable")
        }
    }

    private suspend fun getDestinationAccount(rt: RT, destination: Address, token: Token): Pair<Address, Address> {
        val account = rt.rpc.getAccountInfo(destination)
        if (account.owner == SystemProgram.programID) {
            val pda = AssociatedTokenAccount.getAssociatedTokenAccountAddress(
                destination,
                token.mint.address,
                token.mint.programID
            )
            return pda to destination
        }

        if (account.owner != token.mint.programID) {
            throw Exception("Destination address is a token of different program")
        }

        val tokenAccount = decodeTokenAccount(account)
        if (tokenAccount.mint != token.mint.address) {
            throw Exception("Destination address holds different token")
        }

        return destination to tokenAccount.owner
    }
}
