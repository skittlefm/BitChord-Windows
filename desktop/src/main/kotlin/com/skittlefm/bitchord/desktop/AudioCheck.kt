package com.skittlefm.bitchord.desktop

import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.State
import java.awt.EventQueue
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.util.concurrent.TimeUnit

object AudioCheck {

    @JvmStatic
    fun main(args: Array<String>) {
        val file = chooseAudioFile()

        if (file == null) {
            println("[Áudio] Seleção cancelada.")
            return
        }

        check(file.isFile) {
            "O arquivo selecionado não foi encontrado."
        }

        println("[Áudio] Java: ${System.getProperty("java.version")}")
        println("[Áudio] Arquitetura: ${System.getProperty("os.arch")}")
        println("[Áudio] Carregando o motor de áudio...")

        val factory = MediaPlayerFactory(
            "--no-video",
            "--no-metadata-network-access",
        )

        try {
            println("[Áudio] VLC: ${factory.application().version()}")

            val player = factory.mediaPlayers().newMediaPlayer()

            try {
                println("[Áudio] Arquivo: ${file.absolutePath}")

                val accepted = player.media().play(
                    file.absolutePath,
                )

                check(accepted) {
                    "O motor não conseguiu abrir o arquivo selecionado."
                }

                waitForState(player, State.PLAYING)

                println("[Áudio] Tocando por 4 segundos...")
                Thread.sleep(4_000)

                player.controls().setPause(true)
                waitForState(player, State.PAUSED)

                println("[Áudio] Pausado por 2 segundos...")
                Thread.sleep(2_000)

                player.controls().setPause(false)
                waitForState(player, State.PLAYING)

                println("[Áudio] Reprodução retomada por 4 segundos...")
                Thread.sleep(4_000)

                player.controls().stop()
                waitForState(player, State.STOPPED)

                println("[Áudio] Teste concluído.")
            } finally {
                try {
                    player.controls().stop()
                } finally {
                    player.release()
                }
            }
        } finally {
            factory.release()
        }
    }

    private fun chooseAudioFile(): File? {
        var selected: File? = null

        EventQueue.invokeAndWait {
            val dialog = FileDialog(
                null as Frame?,
                "Selecione um áudio com pelo menos 20 segundos",
                FileDialog.LOAD,
            )

            try {
                dialog.isVisible = true
                selected = dialog.files.firstOrNull()
            } finally {
                dialog.dispose()
            }
        }

        return selected
    }

    private fun waitForState(
        player: MediaPlayer,
        expected: State,
    ) {
        val deadline =
            System.nanoTime() + TimeUnit.SECONDS.toNanos(10)

        while (System.nanoTime() < deadline) {
            val current = player.status().state()

            if (current == expected) {
                return
            }

            check(current != State.ERROR) {
                "O VLC informou um erro durante a reprodução."
            }

            Thread.sleep(50)
        }

        error(
            "O player não chegou ao estado $expected. " +
                    "Estado atual: ${player.status().state()}",
        )
    }
}