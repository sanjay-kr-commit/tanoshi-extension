package extension.linkExtractor

import com.squareup.okhttp.HttpUrl
import com.squareup.okhttp.OkHttpClient
import com.squareup.okhttp.Request
import tanoshi.lib.util.toHtml
import tanoshi.lib.util.toJsoup
import tanoshi.source.api.model.component.Video
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

// code is copied from aniyomi-extension gogoanime

object GogoCdnExtractor {

    fun videosFromUrl( url : String ) : List<Video> {
        val list = ArrayList<Video>()
        val doc = url.toHtml().toJsoup()
        val iv = doc.select("div.wrapper")
            .attr("class").substringAfter("container-")
            .filter { it.isDigit() }.toByteArray()
        val secretKey = doc.select("body[class]")
            .attr("class").substringAfter("container-")
            .filter { it.isDigit() }.toByteArray()
        val decryptionKey = doc.select("div.videocontent")
            .attr("class").substringAfter("videocontent-")
            .filter { it.isDigit() }.toByteArray()
        val encryptAjaxParams = doc.select("script[data-value]")
            .attr("data-value").coder(
                iv , secretKey , false
            )

        val httpUrl = HttpUrl.parse( url )
        val host = "https://" + httpUrl.host() + "/"
        val id = httpUrl.queryParameter("id") ?: throw Exception("error getting id")
        val encryptedId = id.coder( iv, secretKey)
        val jsonResponse = OkHttpClient().newCall(
            Request.Builder()
                .url(
                    "${host}encrypt-ajax.php?id=$encryptedId&$encryptAjaxParams&alias=$id"
                )
                .addHeader(
                    "X-Requested-With", "XMLHttpRequest"
                )
                .get()
                .build()
        ).execute().body()!!.string()

        val data = jsonResponse.substring( 0 , jsonResponse.lastIndexOf( "\"" ) )
            .run { substring( lastIndexOf( "\"" )+1 ) }

        val decryptedData = data.coder( iv, decryptionKey, false)

        val fileUrl = decryptedData.substring( decryptedData.indexOf( "https" ) )
            .run {
                substring( 0 , indexOf( "\"" ) )
            }.replace( "\\" , "" ).trim()

//        list.add(
//            Video(
//                url = fileUrl ,
//                quality = "Auto"
//            )
//        )

        try {
            fileUrl.toHtml().toJsoup().toString().run {
                substring(indexOf("#EXT-X-STREAM-INF:"))
                    .split("#EXT-X-STREAM-INF:").forEach {
                        try {
                            val vid = it.substring(0, it.indexOf("m3u8") + 4)
                            val videoEntry = Video()
                            videoEntry.url =
                                fileUrl.substring(0, fileUrl.lastIndexOf("/") + 1) + vid.substring(vid.indexOf("ep"))
                            videoEntry.quality = vid.substring(vid.indexOf("RESOLUTION")).run {
                                substring(0, indexOf(",")).replace(
                                    "RESOLUTION=", ""
                                )
                            }
                            list.add(videoEntry)
                        } catch (_: Exception) {
                        }
                    }
            }
        }catch (_:Exception){}

        return list
    }

    private fun String.coder(
        iv : ByteArray ,
        key : ByteArray ,
        operation : Boolean = true
    ) : String {
        val ivParameterSpec = IvParameterSpec(iv)
        val secretKey = SecretKeySpec( key , "AES")
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        return if ( operation ) {
            cipher.init( Cipher.ENCRYPT_MODE , secretKey , ivParameterSpec )
            Base64.getEncoder().encodeToString( cipher.doFinal(this.toByteArray()) )
        } else {
           cipher.init( Cipher.DECRYPT_MODE , secretKey , ivParameterSpec )
            String( cipher.doFinal( Base64.getMimeDecoder().decode( this ) ) )
        }
    }

}