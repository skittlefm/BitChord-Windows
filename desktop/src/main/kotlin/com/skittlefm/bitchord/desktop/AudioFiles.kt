package com.skittlefm.bitchord.desktop

import java.awt.EventQueue
import java.awt.FileDialog
import java.awt.Frame
import java.awt.KeyboardFocusManager
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JOptionPane

object AudioFiles {
    private val showing = AtomicBoolean(false)

    fun open(
        canOpen: () -> Boolean,
        onSelected: (List<File>) -> Unit,
    ) {
        // Impede que a repetição da tecla abra vários seletores ao mesmo tempo.
        if (!showing.compareAndSet(false, true)) return

        EventQueue.invokeLater {
            val owner = KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .activeWindow as? Frame

            try {
                if (!canOpen()) return@invokeLater

                val dialog = FileDialog(owner, "Abrir arquivos de áudio", FileDialog.LOAD)
                try {
                    dialog.isMultipleMode = true
                    dialog.isVisible = true

                    val selected = dialog.files.toList()
                    if (selected.isNotEmpty() && canOpen()) onSelected(selected)
                } finally {
                    dialog.dispose()
                }
            } catch (error: Exception) {
                println("[Arquivos] Falha ao abrir o seletor.")
                error.printStackTrace()
                if (canOpen()) {
                    JOptionPane.showMessageDialog(
                        owner,
                        "Não foi possível abrir os arquivos. Tente novamente.",
                        "BitChord Windows",
                        JOptionPane.ERROR_MESSAGE,
                    )
                }
            } finally {
                showing.set(false)
            }
        }
    }
}