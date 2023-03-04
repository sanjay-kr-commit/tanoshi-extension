package extension.en.novel

import tanoshi.lib.exception.PageIndexOutOfTheBoundException
import tanoshi.lib.util.toHtml
import tanoshi.lib.util.toJsoup
import tanoshi.source.api.annotation.EXTENSION
import tanoshi.source.api.annotation.IMPLEMENTED
import tanoshi.source.api.annotation.TAB
import tanoshi.source.api.enum.TYPES
import tanoshi.source.api.model.NovelExtension
import tanoshi.source.api.model.component.Novel
import tanoshi.source.api.model.component.NovelChapter


@EXTENSION( TYPES.NOVEL )
class ReadNovelFull : NovelExtension {
    
    override val lang: String = "En"

    override val name: String = "ReadNovelOnline"
    
    override val DOMAIN_MIRROR: List<String> = listOf( "https://readnovelfull.com" )

    override var baseUrl: String = "https://readnovelfull.com"

    @IMPLEMENTED
    override fun fetchChapterContent(chapter: NovelChapter): String {
        val content : StringBuilder = StringBuilder()
        content.append(
            chapter.url.toHtml().toJsoup()
                .getElementById( "chr-content" )
        )
        return content.toString()
    }

    @IMPLEMENTED
    override fun fetchChapterList(novel: Novel): List<NovelChapter> {
        val chapterList = ArrayList<NovelChapter>()
        val novelId = novel.url.toHtml().toJsoup()
            .select( "div[id=rating]" )[0]
            .attr( "data-novel-id" )
        val table = "${baseUrl}/ajax/chapter-archive?novelId=$novelId"
            .toHtml().toJsoup().getElementsByTag( "li" )
        println( table )
        table.forEach { chapter ->
            try {
                NovelChapter().apply {
                    chapter_number = chapter.text()
                        .run { substring(0, indexOf(":")) }
                        .filter { it.isDigit() || it == '.' || it == '-' }
                        .replace("-", ".")
                        .toFloat()
                    name = chapter.text()
                    url = "$baseUrl${chapter.select("a").attr("href")}"
                    chapterList.add(this)
                }
            }catch ( _ : Exception){}
        }
        return chapterList
    }

    override fun fetchDetails(content: Novel): Novel {
        content.url.toHtml()
            .toJsoup()
            .let { doc ->
                content.run {
                    doc.select( "ul.info-meta > li" ).let { details ->
                        alternativeName = details[0].text()
                        author = details[1].text()
                        genre = details[2].text()
                        source = details[3].text()
                        status = details[4].text()
                    }
                    description = doc.select( "div.desc-text > p" ).text()
                }
            }
        return content
    }

    @IMPLEMENTED
    @TAB
    fun latest(pageIndex: Int): List<Novel> {
        val novelList = ArrayList<Novel>()
        val table = "${baseUrl}/novel-list/latest-release-novel?page=$pageIndex"
            .toHtml()
            .toJsoup()
            .also {
                isIndexValid( pageIndex , "${baseUrl}/novel-list/latest-release-novel" )
            }
            .select( "div[class=list list-novel col-xs-12] > div.row" )
        table.forEach { novel ->
            Novel( this ).apply {
                novel.select( "div.row > div.col-xs-7 > div > h3 > a").let {
                    title = it.attr( "title" )
                    url = baseUrl + it.attr( "href" )
                }
                thumbnail_url =  url.toHtml().toJsoup()
                    .select( "div.books > div.book > img" ).attr( "src" )
                novelList.add( this )
            }

        }
        return novelList
    }

    @IMPLEMENTED
    @TAB
    fun popular(pageIndex: Int): List<Novel> {
        val novelList = ArrayList<Novel>()
        val table  = "${baseUrl}/novel-list/hot-novel?page=$pageIndex"
            .toHtml()
            .toJsoup()
            .also {
                isIndexValid( pageIndex , "${baseUrl}/novel-list/hot-novel" )
            }
            .select( "div[class=list list-novel col-xs-12] > div.row" )
        table.forEach { novel ->
            Novel( this ).apply {
                novel.select( "div.row > div.col-xs-7 > div > h3 > a").let {
                    title = it.attr( "title" )
                    url = baseUrl + it.attr( "href" )
                }
                thumbnail_url =  url.toHtml().toJsoup()
                    .select( "div.books > div.book > img" ).attr( "src" )
                novelList.add( this )
            }
        }
        return novelList
    }

    @IMPLEMENTED
    override fun search(name: String, pageIndex: Int): List<Novel> {
        val novelList = ArrayList<Novel>()
        val queryPath = "${baseUrl}/novel-list/search?keyword=${ name.trimEnd().trimStart().replace( "  " , " " ).replace( " " , "+" ) }"
        val table = "$queryPath&page=$pageIndex"
            .toHtml()
            .toJsoup()
            .also {
                isIndexValid( pageIndex , queryPath )
            }
            .select( "div[class=list list-novel col-xs-12] > div.row" )
        table.forEach { novel ->
            Novel( this ).apply {
                novel.select( "div.row > div.col-xs-7 > div > h3 > a").let {
                    title = it.attr( "title" )
                    url = baseUrl + it.attr( "href" )
                }
                thumbnail_url =  url.toHtml().toJsoup()
                    .select( "div.books > div.book > img" ).attr( "src" )
                novelList.add( this )
            }
        }
        return novelList
    }

    private var rememberGetLastPageIndex : Pair<String,Int>? = null

    private fun getLastPageIndex( url : String ) : Int {
        if ( rememberGetLastPageIndex?.first == url ) return rememberGetLastPageIndex!!.second
        try {
            url.toHtml()
                .toJsoup()
                .select("ul.pagination > li.last > a").attr("data-page").toInt().inc().let {
                    rememberGetLastPageIndex = Pair(url, it)
                    return it
                }
        } catch ( _ : Exception ) {
            rememberGetLastPageIndex = Pair(url, 1)
            return 1
        }
    }

    private fun isIndexValid( pageIndex: Int , url : String ) {
        getLastPageIndex( url ).let { lastPageIndex ->
            if ( pageIndex > lastPageIndex ) throw PageIndexOutOfTheBoundException( "Given page index : $pageIndex exceeds last page Index : $lastPageIndex" )
        }
    }

}