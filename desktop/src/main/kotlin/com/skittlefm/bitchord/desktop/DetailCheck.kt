package com.skittlefm.bitchord.desktop

import com.music.bitchord.data.DetailRepository
import com.music.bitchord.data.HomeRepository
import com.music.bitchord.data.innertube.Innertube
import kotlinx.coroutines.runBlocking

object DetailCheck {

    @JvmStatic
    fun main(args: Array<String>) {
        runBlocking {
            try {
                val browseId = args.firstOrNull() ?: run {
                    println("[Detalhes] Procurando uma playlist no início...")

                    val feed = HomeRepository.home()

                    feed.shelves
                        .flatMap { it.items }
                        .mapNotNull { it.browseId }
                        .firstOrNull {
                            it.startsWith("VL") || it.startsWith("MPREb")
                        }
                        ?: error(
                            "O feed não trouxe uma playlist ou álbum para testar.",
                        )
                }

                println("[Detalhes] Consultando: $browseId")

                val page = DetailRepository.browseSongs(browseId)

                println("[Detalhes] Título: ${page.header?.title ?: browseId}")
                println("[Detalhes] Faixas nesta parte: ${page.songs.size}")
                println("[Detalhes] Sugestões separadas: ${page.suggested.size}")

                check(page.songs.isNotEmpty()) {
                    "Nenhuma faixa encontrada neste item. Envie esta saída para analisarmos."
                }

                page.songs.take(5).forEachIndexed { index, song ->
                    println(
                        "  ${index + 1}. ${song.title} — ${song.artist}",
                    )
                    println(
                        "     Duração: ${song.durationText ?: "não informada"}",
                    )
                }

                val token = page.continuation

                if (token != null) {
                    println("[Detalhes] Consultando a próxima parte...")

                    val nextPage = DetailRepository.moreSongs(token)

                    println(
                        "[Detalhes] Mais faixas recebidas: ${nextPage.songs.size}",
                    )
                    println(
                        "[Detalhes] Mais sugestões: ${nextPage.suggested.size}",
                    )
                } else {
                    println("[Detalhes] A resposta não indica outra parte.")
                }

                println("[Detalhes] Consulta concluída.")
            } finally {
                Innertube.close()
            }
        }
    }
}