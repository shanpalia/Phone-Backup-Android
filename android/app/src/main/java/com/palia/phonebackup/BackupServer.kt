package com.palia.phonebackup

import android.content.Context
import android.util.Base64
import androidx.documentfile.provider.DocumentFile
import fi.iki.elonen.NanoHTTPD
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream

class BackupServer(
    private val context: Context,
    private val roots: List<DocumentFile>,
    private val code: String
) : NanoHTTPD("0.0.0.0", 8765) {

    override fun serve(session: IHTTPSession): Response {
        val params = session.parameters
        val supplied = params["code"]?.firstOrNull() ?: ""
        if (supplied != code) return newFixedLengthResponse(Response.Status.FORBIDDEN, "text/plain", "Invalid pairing code")
        return try {
            when (session.uri) {
                "/api/status" -> json(JSONObject().put("app", "Phone Backup").put("version", 1).put("sharedRoots", roots.size))
                "/api/files" -> json(JSONObject().put("items", JSONArray().also { arr -> roots.forEach { addTree(arr, it, it.name ?: "Folder") } }))
                "/api/download" -> download(params["id"]?.firstOrNull() ?: "")
                else -> newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not found")
            }
        } catch (e: Exception) {
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", e.message ?: "Server error")
        }
    }

    private fun addTree(out: JSONArray, node: DocumentFile, relative: String) {
        if (node.isDirectory) {
            out.put(JSONObject().put("id", encode(node.uri.toString())).put("name", node.name ?: relative.substringAfterLast('/')).put("path", relative).put("type", "dir"))
            node.listFiles().forEach { child -> addTree(out, child, "$relative/${child.name ?: "item"}") }
        } else if (node.isFile) {
            out.put(JSONObject().put("id", encode(node.uri.toString())).put("name", node.name ?: "file").put("path", relative).put("type", "file").put("size", node.length()))
        }
    }

    private fun download(encoded: String): Response {
        val uriString = String(Base64.decode(encoded, Base64.URL_SAFE or Base64.NO_WRAP), Charsets.UTF_8)
        val uri = android.net.Uri.parse(uriString)
        val file = roots.asSequence().flatMap { sequenceOf(it) + it.walkSequence() }.firstOrNull { it.uri == uri }
            ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "File not found or not shared")
        if (!file.isFile) return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "Not a file")
        val input: InputStream = context.contentResolver.openInputStream(uri)
            ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Cannot open file")
        val name = file.name ?: "backup.bin"
        val response = newChunkedResponse(Response.Status.OK, context.contentResolver.getType(uri) ?: "application/octet-stream", input)
        response.addHeader("Content-Disposition", "attachment; filename*=UTF-8''" + java.net.URLEncoder.encode(name, "UTF-8").replace("+", "%20"))
        return response
    }

    private fun DocumentFile.walkSequence(): Sequence<DocumentFile> = sequence {
        if (isDirectory) for (child in listFiles()) {
            yield(child)
            if (child.isDirectory) yieldAll(child.walkSequence())
        }
    }

    private fun encode(value: String): String = Base64.encodeToString(value.toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP)
    private fun json(obj: JSONObject): Response = newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", obj.toString())
}
