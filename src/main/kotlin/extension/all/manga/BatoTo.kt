package extension.all.manga

import tanoshi.extension.cryptoaes.CryptoAES
import tanoshi.extension.cryptoaes.Deobfuscator
import tanoshi.lib.exception.PageIndexOutOfTheBoundException
import tanoshi.lib.util.toHtml
import tanoshi.lib.util.toJsoup
import tanoshi.source.api.annotation.EXTENSION
import tanoshi.source.api.annotation.IMPLEMENTED
import tanoshi.source.api.annotation.TAB
import tanoshi.source.api.enum.TYPES
import tanoshi.source.api.model.MangaExtension
import tanoshi.source.api.model.component.Manga
import tanoshi.source.api.model.component.MangaChapter
import tanoshi.source.api.model.component.MangaPage
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

@EXTENSION( TYPES.MANGA )
class BatoTo : MangaExtension {

    override val lang: String = "multi"

    override val DOMAIN_MIRROR: List<String> = listOf( "https://bato.to" )

    override val baseUrl: String = "https://bato.to"

    override val name: String = "bato"

    @IMPLEMENTED
    @TAB
    fun latest(pageIndex: Int): List<Manga> {
        val mangaList = ArrayList<Manga>()
        if ( pageIndex == 1 ) mangaList.addAll( fetchFirstLatestPage() ).also { return mangaList }
        val table = "$baseUrl/latest?page=$pageIndex"
            .toHtml().run {
                substring(indexOf("html"), lastIndexOf("}"))
                    .replace("\\n", "")
                    .replace("\"", "")
                    .replace("\\", "")
            }.toJsoup()
            .select( "div[class~=col]" )
            .also {
                if ( it.size == 0 ) throw PageIndexOutOfTheBoundException( "Found Nothing on page $pageIndex" )
            }
        table.forEach { manga ->
            Manga( this ).apply {
                lang = manga.select( "em" ).attr( "data-lang" )
                    .trim().let {
                        it.ifBlank { "En" }
                    }
                title = manga.getElementsByClass( "item-title" ).text()
                url = "$baseUrl${manga.getElementsByClass( "item-cover" ).attr("href")}"
                thumbnail_url = manga.getElementsByTag("img").attr( "src" )
                mangaList.add( this )
            }
        }
        return mangaList
    }

    private fun fetchFirstLatestPage() : List<Manga> {
        val mangaList = ArrayList<Manga>()
        val table = "$baseUrl/latest"
            .toHtml().toJsoup()
            .getElementById( "series-list" )!!.toString().run {
                substring( indexOf( "\n" )+1 , lastIndexOf( "</div>" ) )
            }.toJsoup()
            .select( "div[class~=col item line-b]" )
        table.forEach { manga ->
            Manga( this ).apply {
                lang = manga.select( "em" ).attr( "data-lang" )
                    .trim().let {
                        it.ifBlank { "En" }
                    }
                title = manga.getElementsByClass( "item-title" ).text()
                url = "$baseUrl${manga.getElementsByClass( "item-cover" ).attr("href")}"
                thumbnail_url = manga.getElementsByTag("img").attr( "src" )
                mangaList.add( this )
            }
        }
        return mangaList
    }

    @IMPLEMENTED
    @TAB
    fun popular(pageIndex: Int): List<Manga> {
        val mangaList = ArrayList<Manga>()
        val table = "$baseUrl/browse?sort=views_a.za?page=$pageIndex"
            .toHtml()
            .toJsoup()
            .also {
                it.select( "ul.pagination > li" )
                    .let { i -> i[ i.size-2 ] }
                    .text()
                    .filter { i -> i.isDigit() }.toInt().let { lastIndex ->
                    if ( pageIndex > lastIndex ) throw PageIndexOutOfTheBoundException( "Given page index : $pageIndex exceeds last page Index : $lastIndex" )
                }
            }
            .select( "div[id~=series-list] > div.item" )
        table.forEach { manga ->
            Manga( this ).apply {
                lang = manga.select( "em" ).attr( "data-lang" )
                    .trim().let {
                        it.ifBlank { "En" }
                    }
                title = manga.getElementsByClass( "item-title" ).text()
                url = "$baseUrl${manga.getElementsByClass( "item-cover" ).attr("href")}"
                thumbnail_url = manga.getElementsByTag("img").attr( "src" )
                mangaList.add( this )
            }
        }
        return mangaList
    }

    @IMPLEMENTED
    override fun fetchChapterList(manga: Manga): List<MangaChapter> {
        val chapterList = ArrayList<MangaChapter>()
        val table = manga.url.toHtml().toJsoup()
            .select( "div.episode-list > div.main > div.item" )
        table.reversed().forEach { chapter ->
            MangaChapter().apply {
                chapter.select( "a" ).let { tag ->
                    chapter_number = tag.text().filter { it.isDigit() || it == '.' }.toFloat()
                    name = tag.text()
                    url = baseUrl+tag.attr( "href" )
                }
                upload_date = formatTime( chapter.select( "i.ps-3" ).text().filter { it.isDigit() }.toLong() )
                chapterList.add( this )
            }
        }
        return chapterList
    }

    @IMPLEMENTED
    override fun fetchDetails(content: Manga): Manga {
        content.url
            .toHtml()
            .toJsoup()
            .run {
                content.thumbnail_url = select( "div.attr-cover > img" ).attr( "src" )
                select( "div.attr-main" )
                    .also {
                        content.description = it.select( "div.mt-3 > div > div > div.limit-html" ).text()
                    }
                    .select( "div.attr-item" )
                    .forEach {
                        val attr = it.text()
                        when  {
                            attr.startsWith( "Authors:" ) -> content.author = attr
                            attr.startsWith("Artists:") -> content.artist = attr
                            attr.startsWith( "Genres:" ) -> content.genre = attr
                            attr.startsWith( "Upload status:" ) -> content.status = attr
                        }
                    }
            }
        return content
    }

    @IMPLEMENTED
    override fun fetchPageList(chapter: MangaChapter): List<MangaPage> {
        val pages = ArrayList<MangaPage>()
        val html = chapter.url.toHtml()
        val table = html.toJsoup()
            .toString().run {
                val array = Regex( "imgHttpLis.*" ).find( this )
                Regex( "\".*\"" ).find( array!!.value )!!
                    .value
                    .split( "," )
            }

        val batoPass = Regex( "const batoPass .*" ).find( html )!!.value.run {
            substring( indexOf( "[" ) , lastIndexOf( ";" ) )
        }
        val batoWord = Regex( "const batoWord .*" ).find( html )!!.value.run {
            substring( indexOf( "\"" )+1 , lastIndexOf( "\"" ) )
        }
        val evaluatedPass : String = Deobfuscator.deobfuscateJsPassword( batoPass )
        val imgAccListString = CryptoAES.decrypt(batoWord, evaluatedPass).run {
            substring( indexOf( "[" )+1 , lastIndexOf( "]" ) ).split( "," )
        }

        table.indices.forEach { i ->
            MangaPage(
                i+1 , "${table[i].run { substring( 1 , length-1 ) }}?${imgAccListString[i].run { substring( 1 , length-1 ) }}"
            ).let { pages.add( it ) }
        }

        return pages
    }

    @IMPLEMENTED
    override fun search(name: String, pageIndex: Int): List<Manga> {
        val mangaList = ArrayList<Manga>()
        val table = "$baseUrl/search?word=${name.trimStart().trimEnd().replace( "  " , " " ).replace( " " , "+" )}&page=$pageIndex"
            .toHtml()
            .toJsoup()
            .also {
                it.select( "ul.pagination > li" )
                    .let { i -> i[ i.size-2 ] }
                    .text()
                    .filter { i -> i.isDigit() }.toInt().let { lastIndex ->
                        if ( pageIndex > lastIndex ) throw PageIndexOutOfTheBoundException( "Given page index : $pageIndex exceeds last page Index : $lastIndex" )
                    }
            }
            .select( "div.series-list > div.item" )
        table.forEach { manga ->
            Manga( this ).apply {
                lang = manga.select( "em" ).attr( "data-lang" )
                    .trim().let {
                        it.ifBlank { "En" }
                    }
                title = manga.getElementsByClass( "item-title" ).text()
                url = "$baseUrl${manga.getElementsByClass( "item-cover" ).attr("href")}"
                thumbnail_url = manga.getElementsByTag("img").attr( "src" )
                mangaList.add( this )
            }
        }
        return mangaList
    }

     private fun formatTime( dayPrior : Long ) : String {
        return Calendar.getInstance().timeInMillis.let { today ->
            ( dayPrior*86400000 ).let { daysPriorInMilliSeconds ->
                val formatter = DateTimeFormatter.ofPattern( "dd/MM/YYYY" )
                val instant = Instant.ofEpochMilli( today - daysPriorInMilliSeconds )
                val date = LocalDateTime.ofInstant( instant , ZoneId.systemDefault() )
                formatter.format( date )
            }
        }
    }

}