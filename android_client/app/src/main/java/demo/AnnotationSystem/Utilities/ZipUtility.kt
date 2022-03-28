package demo.AnnotationSystem.Utilities

import android.os.Environment
import android.util.Log
import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.Deflater

// https://gist.github.com/juange87/5796241

class Compress(private val _path: File, private val _files: Array<String>, private val _zipFile: String) {

    fun zip() {
        try {
            var origin: BufferedInputStream?
            val dest = FileOutputStream(File(_path,_zipFile))

            val out = ZipOutputStream(BufferedOutputStream(dest))
            out.setLevel(Deflater.NO_COMPRESSION)

            val data = ByteArray(BUFFER)

            for (i in _files.indices) {
                Log.v("Compress", "Adding: " + _files[i])
                val file = File(_path, _files[i])
                if (!file.exists()) continue
                val fi = FileInputStream(file)
                origin = BufferedInputStream(fi, BUFFER)
                val entry = ZipEntry(_files[i].substring(_files[i].lastIndexOf("/") + 1))
                out.putNextEntry(entry)
                var count = origin.read(data, 0, BUFFER)
                while (count != -1) {
                    out.write(data, 0, count)
                    count = origin.read(data, 0, BUFFER)
                }
                origin.close()
            }

            out.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }

    }

    companion object {
        private val BUFFER = 2048
    }
}


fun multipartRequest(urlTo: String, params: Map<String, String>?, filepath: String, filefield: String, fileMimeType: String): String {
    var connection: HttpURLConnection? = null
    var outputStream: DataOutputStream? = null
    var inputStream: InputStream? = null

    val twoHyphens = "--"
    val boundary = "*****" + java.lang.Long.toString(System.currentTimeMillis()) + "*****"
    val lineEnd = "\r\n"

    var result = ""

    var bytesRead: Int
    var bytesAvailable: Int
    var bufferSize: Int
    val buffer: ByteArray
    val maxBufferSize = 1 * 1024 * 1024

    val q = filepath.split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
    val idx = q.size - 1

    try {
        val file = File(filepath)
        val fileInputStream = FileInputStream(file)

        val url = URL(urlTo)
        connection = url.openConnection() as HttpURLConnection

        connection.setDoInput(true)
        connection.setDoOutput(true)
        connection.setUseCaches(false)

        connection.setRequestMethod("POST")
        connection.setRequestProperty("Connection", "Keep-Alive")
        connection.setRequestProperty("User-Agent", "Android Multipart HTTP Client 1.0")
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")

        outputStream = DataOutputStream(connection.getOutputStream())
        outputStream.writeBytes(twoHyphens + boundary + lineEnd)
        outputStream.writeBytes("Content-Disposition: form-data; name=\"" + filefield + "\"; filename=\"" + q[idx] + "\"" + lineEnd)
        outputStream.writeBytes("Content-Type: $fileMimeType$lineEnd")
        outputStream.writeBytes("Content-Transfer-Encoding: binary$lineEnd")

        outputStream.writeBytes(lineEnd)

        bytesAvailable = fileInputStream.available()
        bufferSize = Math.min(bytesAvailable, maxBufferSize)
        buffer = ByteArray(bufferSize)

        bytesRead = fileInputStream.read(buffer, 0, bufferSize)
        while (bytesRead > 0) {
            outputStream.write(buffer, 0, bufferSize)
            bytesAvailable = fileInputStream.available()
            bufferSize = Math.min(bytesAvailable, maxBufferSize)
            bytesRead = fileInputStream.read(buffer, 0, bufferSize)
        }

        outputStream.writeBytes(lineEnd)

        // Upload POST Data
        if (params != null) {
            for ((key, value) in params) {
                outputStream.writeBytes(twoHyphens + boundary + lineEnd)
                outputStream.writeBytes("Content-Disposition: form-data; name=\"$key\"$lineEnd")
                outputStream.writeBytes("Content-Type: text/plain$lineEnd")
                outputStream.writeBytes(lineEnd)
                outputStream.writeBytes(value)
                outputStream.writeBytes(lineEnd)
            }
            outputStream.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd)
        }


        if (connection.responseCode != 200 && connection.responseCode != 201) {
            Log.e(PROXIES_PACKAGE_NAME, "Response code:" + connection.responseCode + " " + connection.responseMessage)
            return ""
        }

        inputStream = connection.inputStream

        result = convertStreamToString(inputStream)

        fileInputStream.close()
        inputStream.close()
        outputStream.flush()
        outputStream.close()

        return result
    } catch (e: Exception) {
        e.printStackTrace()
    } finally {
        if (connection != null) {
            try {
            connection.inputStream.close();
            } catch (e: IOException) {
                e.printStackTrace()
            }
            connection.disconnect()
        }
    }
    return ""
}

private fun convertStreamToString(inputStream: InputStream): String {
    val reader = BufferedReader(InputStreamReader(inputStream))
    val sb = StringBuilder()

    try {
        var line = reader.readLine()
        while (line != null) {
            sb.append(line)
            line = reader.readLine()
        }
    } catch (e: IOException) {
        e.printStackTrace()
    } finally {
        try {
            inputStream.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }

    }
    return sb.toString()
}