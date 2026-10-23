package com.rokidvim.sample

import com.rokidvim.core.FileStore
import java.io.File

/**
 * アプリ内部ストレージ上の [FileStore] 実装（UTF-8）。
 * パス区切りを含む名前は拒否してディレクトリの外に出られないようにする。
 */
class AndroidFileStore(private val dir: File) : FileStore {

    init {
        dir.mkdirs()
    }

    override fun read(name: String): String? {
        val f = resolve(name) ?: return null
        if (!f.isFile) return null
        return f.readText(Charsets.UTF_8)
    }

    override fun write(name: String, content: String) {
        val f = resolve(name) ?: throw IllegalArgumentException("invalid name: $name")
        val tmp = File(dir, "${f.name}.tmp")
        tmp.writeText(content, Charsets.UTF_8)
        if (!tmp.renameTo(f)) {
            f.writeText(content, Charsets.UTF_8)
            tmp.delete()
        }
    }

    private fun resolve(name: String): File? {
        if (name.isEmpty() || name.contains('/') || name.contains('\\') || name == "." || name == "..") {
            return null
        }
        return File(dir, name)
    }
}
