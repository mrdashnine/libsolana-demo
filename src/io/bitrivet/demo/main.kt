package io.bitrivet.demo

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import io.bitrivet.blockchain.solana.core.Commitment
import io.bitrivet.blockchain.solana.core.signer.KeyPairSigner
import io.bitrivet.blockchain.solana.rpc.RPC
import io.bitrivet.demo.command.Balance
import java.nio.file.Path

class CLI : CliktCommand() {
    private val configPath by option("-c", help = "path to config file").path().default(Path.of("config.yml"))
    override fun run() {
        val config = readConfig(configPath)
        val signer = KeyPairSigner.loadKeyPairFromFile(config.keyPath)
        val rt = RT(
            config,
            RPC(config.endpoint, null, Commitment.CONFIRMED),
            signer,
        )
        currentContext.obj = rt
    }
}

fun main(args: Array<String>) = CLI().subcommands(
    Balance(),
).main(args)

private fun readConfig(path: Path): Config {
    val yamlMapper = ObjectMapper(YAMLFactory()).apply {
        registerKotlinModule()
    }

    val fileContent = path.toFile().readText()

    val result = yamlMapper.readValue<Config>(fileContent)
    return result
}
