package extension.en.anime

import extension.linkExtractor.GogoCdnExtractor.videosFromUrl
import tanoshi.lib.exception.PageIndexOutOfTheBoundException
import tanoshi.lib.util.toHtml
import tanoshi.lib.util.toJsoup
import tanoshi.source.api.annotation.EXTENSION
import tanoshi.source.api.annotation.IMPLEMENTED
import tanoshi.source.api.annotation.TAB
import tanoshi.source.api.enum.TYPES
import tanoshi.source.api.model.AnimeExtension
import tanoshi.source.api.model.component.Anime
import tanoshi.source.api.model.component.AnimeEpisode
import tanoshi.source.api.model.component.Video

@EXTENSION(TYPES.ANIME)
class Gogoanime : AnimeExtension {

    override val baseUrl: String = "https://gogoanime.ar"

    override val lang: String = "En"

    override val name: String = "Gogoanime"

    override val DOMAIN_MIRROR: List<String> = listOf( "https://gogoanime.bid" )
    
    override fun fetchDetails(content: Anime): Anime {
        content.url.toHtml()
            .toJsoup()
            .select( "div.anime_info_body_bg > p" )
            .let { details ->
                content.run {
                    type = details[0].text()
                    description = details[1].text()
                    genre = details[2].text()
                    airDate = details[3].text()
                    status = details[4].text()
                    alternativeName = details[5].text()
                }
            }
        return content
    }

    @IMPLEMENTED
    override fun fetchEpisodeLink(episode: AnimeEpisode): List<Video> {
        val episodeUrls = ArrayList<Video>()
        val doc = episode.url
            .toHtml().toJsoup()
        // GogoCdn
        doc.select( "div.anime_muti_link > ul > li.vidcdn > a" )
            .firstOrNull()?.attr( "data-video" )
            ?.let { episodeUrls.addAll( videosFromUrl( "https:$it" ) ) }
        // Vidstreaming
        doc.select( "div.anime_muti_link > ul > li.anime > a" )
            .firstOrNull()?.attr( "data-video" )
            ?.let { episodeUrls.addAll( videosFromUrl( "https:$it" ) ) }
        return episodeUrls
    }

    @IMPLEMENTED
    override fun fetchEpisodeList(anime: Anime): List<AnimeEpisode> {
        val episodeList = ArrayList<AnimeEpisode>()

        val range = anime.url.toHtml().toJsoup()
            .select( "ul[id=episode_page] > li" )
            .let {
                Pair(
                    it.first()!!.text().let { range ->
                        range.substring( 0 , range.indexOf( "-" ) )
                    } , it.last()!!.text().let { range ->
                        range.substring( range.indexOf( "-" )+1 )
                    }
                )
            }

        if ( anime.id == null ) parseAnimeId( anime )

        "https://ajax.gogo-load.com/ajax/load-list-episode?ep_start=${range.first}&ep_end=${range.second}&id=${anime.id}"
            .toHtml().toJsoup()
            .getElementsByTag( "a" )
            .reversed()
            .forEach {
            AnimeEpisode().apply{
                episode_number = it.text().filter { it.isDigit() || it == '.' }.toFloat()
                url = "$baseUrl/${it.attr( "href" ).trim().substring(1)}"
                episodeList.add( this )
            }
        }

        return episodeList
    }

    @IMPLEMENTED
    @TAB
    fun latest(pageIndex: Int): List<Anime> {
        val animeList = arrayListOf<Anime>()
        val table = "https://ajax.gogo-load.com/ajax/page-recent-release-ongoing.html?page=$pageIndex&type=1"
            .toHtml().toJsoup()
            .also {
                it.select( "ul.pagination-list > li" ).last()!!.text().toInt().let { lastIndex ->
                    if ( pageIndex > lastIndex ) throw PageIndexOutOfTheBoundException( "Given page index : $pageIndex exceeds last page Index : $lastIndex" )
                }
            }
            .getElementsByClass( "added_series_body popular" )[0]
            .getElementsByTag("li")
        for ( item in table ) {
            Anime( this ).apply {
                title = item.getElementsByTag("a").attr("title")
                url = "$baseUrl${item.getElementsByTag( "a" ).attr( "href" )}"
                thumbnail_url = item.getElementsByClass( "thumbnail-popular" ).attr( "style" ).run {
                    substring( indexOf( "http" ) , lastIndexOf( ");" )-1 )
                }
            }.run {
                animeList.add( this )
            }
        }
        return animeList
    }

    @IMPLEMENTED
    @TAB
    fun popular(pageIndex: Int): List<Anime> {
        val animeList = ArrayList<Anime>()
        val table = "${baseUrl}/popular.html?page=$pageIndex"
            .toHtml()
            .toJsoup()
            .also {
                it.select( "ul.pagination-list > li" )
                    .let { pageNavigator ->
                        if ( pageNavigator.size == 0 ) throw PageIndexOutOfTheBoundException( "Page Navigator Not Found page $pageIndex have no content" )
                    }
            }
            .select( "ul.items > li" )
        table.forEach { anime ->
            Anime( this ).apply {
                title = anime.select( "a" ).attr( "title" )
                url = baseUrl +"/"+ anime.select( "a" ).attr( "href" ).substring( 1 )
                thumbnail_url = anime.select( "img" ).attr( "src" )
                animeList.add( this@apply )
            }
        }
        return animeList
    }

    @IMPLEMENTED
    override fun search(name: String, pageIndex: Int): List<Anime> {
        val animeList = ArrayList<Anime>()
        val doc = "$baseUrl/search.html?keyword=${name.trimEnd().trimStart().replace( "  " , " " ).replace( " " , "%20" )}&page=$pageIndex"
            .toHtml().toJsoup()
        val pageNavigator = doc.select("ul.pagination-list > li")
        val table = doc.select( "ul.items > li" )
        if ( pageNavigator.size == 0 ) throw PageIndexOutOfTheBoundException( "Page $pageIndex with query $name have no result" )
        else if ( pageIndex > pageNavigator.last()!!.text().toInt() ) throw PageIndexOutOfTheBoundException( "Given page index : $pageIndex exceeds last page Index : ${pageNavigator.last()!!.text()}" )
        for ( element in table ) {
            Anime(this ).apply {
                title = element.getElementsByTag( "a" ).attr( "title" )
                url = "$baseUrl${element.getElementsByTag( "a" ).attr( "href" )}"
                thumbnail_url = element.getElementsByTag("img").attr("src")
            }.let { entry -> animeList.add( entry ) }
        }
        return animeList
    }

    private fun parseAnimeId( name : Anime ) {
        name.id = name.url
            .toHtml()
            .toJsoup()
            .getElementsByClass( "movie_id" )
            .attr( "value" )
            .trim()
    }

}