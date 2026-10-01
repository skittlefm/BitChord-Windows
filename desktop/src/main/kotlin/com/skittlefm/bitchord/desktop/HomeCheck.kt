package com.skittlefm.bitchord.desktop

import com.music.bitchord.data.HomeRepository
import com.music.bitchord.data.innertube.Innertube
import kotlinx.coroutines.runBlocking

object HomeCheck {
    @JvmStatic
    fun main(args: Array<String>) {
        runBlocking {
            try {
                println("[Feed] Consultando o YouTube Music...")

                val feed = HomeRepository.home()
                val itemCount = feed.shelves.sumOf { it.items.size }

                println("[Feed] Seções recebidas: ${feed.shelves.size}")
                println("[Feed] Itens recebidos: $itemCount")

                check(itemCount > 0) {
                    "A consulta terminou, mas o parser não encontrou itens no feed."
                }

                feed.shelves.take(3).forEach { shelf ->
                    println("[Feed] Seção: ${shelf.title}")

                    shelf.items.take(2).forEach { item ->
                        println("  Título: ${item.title}")
                        println("  Subtítulo: ${item.subtitle}")
                        println(
                            "  Referência de capa: " +
                                    if (item.thumbnailUrl.isNullOrBlank()) {
                                        "ausente"
                                    } else {
                                        "presente"
                                    },
                        )
                    }
                }

                println("[Feed] Consulta concluída.")
            } finally {
                Innertube.close()
            }
        }
    }
}