package app.revenge.manager.esharq

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * The installer downloads Discord from mirrors it does not run, so this decision is the only thing
 * standing between a compromised mirror and a modified Discord installed under the Esharq name.
 */
class DiscordSignatureTest {

    private val discord = DiscordSignature.CERTIFICATE_SHA256
    private val somebodyElse = "40ea6475a1192482612b99509e33c3844df214459704bb428c4007ba11de2610"

    @Test
    fun `Discord's own certificate is accepted`() {
        assertIs<DiscordSignature.Result.Genuine>(DiscordSignature.judge(listOf(discord)))
    }

    @Test
    fun `the case it is written in does not matter`() {
        assertIs<DiscordSignature.Result.Genuine>(DiscordSignature.judge(listOf(discord.uppercase())))
    }

    @Test
    fun `anyone else is refused`() {
        assertIs<DiscordSignature.Result.WrongSigner>(DiscordSignature.judge(listOf(somebodyElse)))
    }

    @Test
    fun `Discord plus somebody else is still refused`() {
        val verdict = DiscordSignature.judge(listOf(discord, somebodyElse))
        assertIs<DiscordSignature.Result.WrongSigner>(verdict, "one genuine signer does not excuse another")
    }

    @Test
    fun `an unsigned file is refused rather than waved through`() {
        assertIs<DiscordSignature.Result.Unverifiable>(DiscordSignature.judge(emptyList()))
    }

    @Test
    fun `the digest is the plain lowercase hex of the certificate`() {
        // "abc" in SHA-256, so a wrong encoding — base64, uppercase, colon-separated — is caught
        // here rather than by every comparison silently failing on a real device.
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            DiscordSignature.sha256("abc".toByteArray())
        )
    }
}
