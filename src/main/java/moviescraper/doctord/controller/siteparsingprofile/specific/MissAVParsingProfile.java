package moviescraper.doctord.controller.siteparsingprofile.specific;

import moviescraper.doctord.controller.languagetranslation.Language;
import moviescraper.doctord.controller.siteparsingprofile.SiteParsingProfile;
import moviescraper.doctord.model.SearchResult;
import moviescraper.doctord.model.dataitem.*;
import moviescraper.doctord.model.dataitem.Runtime;
import moviescraper.doctord.scraper.UserAgent;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class MissAVParsingProfile extends SiteParsingProfile implements SpecificProfile {

    public static final String urlLanguageEnglish = "en";
    public static final String urlLanguageJapanese = "ja";

    final String titleTablePath = "div.space-y-2";
    final String titlePath = "h1.text-base";
    final String notFoundpath = "p.text-4xl.font-extrabold.text-primary";
    final String plotTextPath = "div.mb-1";
    final String posterImg = "video.player";
    Map<String, Element> movie_data = new HashMap<>();

    String id, url;
    Document japaneseDocument;

    @Override
    public void prepareData(){
        Element notFoundnum = document.select(notFoundpath).first();
        if(notFoundnum != null && notFoundnum.text().equals("404")){
            System.err.println("Title not found");
        } else {
            Element table = document.select(titleTablePath).first();
            if(table != null){
                for(Element data : table.children()){
                    if(data.firstElementChild() == null){
                        continue;
                    }
                    switch (data.firstElementChild().text()){
                        // Multi-value fields (contain multiple <a> links) keep the whole row element
                        case "Genre:":
                        case "ジャンル:":
                        case "Actors:":
                        case "Actor:":
                        case "俳優:":
                        case "Actress:":
                        case "女優:":
                        case "Tags:":
                        case "タグ:":
                            movie_data.put(data.firstElementChild().text(), data);
                            break;
                        default:
                            movie_data.put(data.firstElementChild().text(), data.lastElementChild());
                            break;
                    }
                }
            }
        }
    }

    private String getUrlLanguageToUse() {
        return (scrapingLanguage == Language.ENGLISH) ? urlLanguageEnglish : urlLanguageJapanese;
    }

    private void initializeJapaneseDocument(){
        try {
            String japaneseUrl;
            if (id != null) {
                japaneseUrl = "https://missav.ws/dm32/" + urlLanguageJapanese + "/" + id;
            } else if (url != null) {
                japaneseUrl = url.replace("/" + urlLanguageEnglish + "/", "/" + urlLanguageJapanese + "/");
            } else {
                return;
            }
            var response = downloadDocumentFromUrl(japaneseUrl).bufferUp();
            if (response.statusCode() == 200) {
                japaneseDocument = response.parse();
            }
        } catch (IOException e) {
            System.err.println(e.getMessage());
        }
    }

    @Override
    public Connection.Response downloadDocumentFromUrl(String url) {
        try {
            var response = Jsoup.connect(url)
                    .userAgent(UserAgent.getRandomUserAgent())
                    // MissAV rejects requests that don't look like a real browser (returns 403),
                    // so send the same headers a browser would.
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
                    .header("Accept-Language", "ja,en-US;q=0.9,en;q=0.8")
                    .header("Sec-Fetch-Dest", "document")
                    .header("Sec-Fetch-Mode", "navigate")
                    .header("Sec-Fetch-Site", "none")
                    .header("Sec-Fetch-User", "?1")
                    .header("Upgrade-Insecure-Requests", "1")
                    .referrer("https://missav.ws/")
                    .followRedirects(true)
                    .ignoreHttpErrors(true)
                    .timeout(CONNECTION_TIMEOUT_VALUE)
                    .execute();
            this.url = response.url().toString();
            return response;
        } catch (IOException e) {
            System.err.println(e.getMessage());
        }
        return null;
    }

    @Nonnull
    @Override
    public Title scrapeTitle() {
        Element title_element = document.select(titlePath).first();
        if(title_element != null){
            return new Title(title_element.text());
        }
        return new Title("");
    }

    @Nonnull
    @Override
    public OriginalTitle scrapeOriginalTitle() {
        initializeJapaneseDocument();
        if(japaneseDocument != null){
            Element japaneseTitle = japaneseDocument.select(titlePath).first();
            if(japaneseTitle != null && !japaneseTitle.text().isBlank()){
                return new OriginalTitle(japaneseTitle.text());
            }
        }
        return OriginalTitle.BLANK_ORIGINALTITLE;
    }

    @Nonnull
    @Override
    public SortTitle scrapeSortTitle() {
        return SortTitle.BLANK_SORTTITLE;
    }

    @Nonnull
    @Override
    public Set scrapeSet() {
        String keyword = (scrapingLanguage == Language.ENGLISH) ? "Series:" : "シリーズ:";
        if(movie_data.containsKey(keyword)){
            Element series = movie_data.get(keyword);
            if(series != null){
                return new Set(series.text());
            }
        }
        return Set.BLANK_SET;
    }

    @Nonnull
    @Override
    public Rating scrapeRating() {
        return Rating.BLANK_RATING;
    }

    @Nonnull
    @Override
    public ReleaseDate scrapeReleaseDate() {
        String keyword = (scrapingLanguage == Language.ENGLISH) ? "Release date:" : "配信開始日:";
        if(movie_data.containsKey(keyword)){
            Element date = movie_data.get(keyword);
            if(date != null){
                return new ReleaseDate(date.text());
            }
        }
        return ReleaseDate.BLANK_RELEASEDATE;
    }

    @Nonnull
    @Override
    public Year scrapeYear() {
        return scrapeReleaseDate().getYear();
    }

    @Nonnull
    @Override
    public Top250 scrapeTop250() {
        return Top250.BLANK_TOP250;
    }

    @Nonnull
    @Override
    public Votes scrapeVotes() {
        return Votes.BLANK_VOTES;
    }

    @Nonnull
    @Override
    public Outline scrapeOutline() {
        return Outline.BLANK_OUTLINE;
    }

    @Nonnull
    @Override
    public Plot scrapePlot() {
        Element plot = document.select(plotTextPath).first();
        if(plot != null && !plot.text().isEmpty()){
            return new Plot(plot.text());
        }
        return Plot.BLANK_PLOT;
    }

    @Nonnull
    @Override
    public Tagline scrapeTagline() {
        // TODO: Implement this once I find an example title to test against
        return Tagline.BLANK_TAGLINE;
    }

    @Nonnull
    @Override
    public Runtime scrapeRuntime() {
        Elements durationElems = document.select(".plyr__time--duration");
        if(!durationElems.isEmpty()){
            String[] durationSplitByTimeUnit = durationElems.first().text().split(":");
            if(durationSplitByTimeUnit.length != 3) {
                System.err.println("Failed to gather movie duration: Invalid number of parts!");
            }
            int hours = Integer.parseInt(durationSplitByTimeUnit[0]);
            int minutes = Integer.parseInt(durationSplitByTimeUnit[1]);
            return new Runtime(Integer.toString((hours * 60) + minutes));
        } else {
            System.err.println("Couldn't find the duration element: `.plyr__time--duration`");
        }
    return Runtime.BLANK_RUNTIME;
}

    @Override
    public Thumb[] scrapePosters(boolean cropPosters) {
        // TODO: Fix for chinese media titles; Need a way to discern Japanese titles from Chinese ones
        try {
            Thumb[] posters = new Thumb[1];
            Element posterElement = document.select(posterImg).first();

            if(posterElement == null)
                // try another way
                posterElement = document.select(".plyr__video-wrapper .player").first();

            if (posterElement != null) {
                posters[0] = new Thumb(posterElement.attr("data-poster"), cropPosters);
                return posters;
            }
        }catch (IOException e){
            System.err.println(e.getMessage());
        }
        return new Thumb[0];
    }

    @Override
    public Thumb[] scrapeFanart() {
        try {
            Thumb[] posters = new Thumb[1];
            Element posterElement = document.select(posterImg).first();
            if (posterElement != null) {
                posters[0] = new Thumb(posterElement.attr("data-poster"), false);
                return posters;
            }
        }catch (IOException e){
            System.err.println(e.getMessage());
        }
        return new Thumb[0];
    }

    @Override
    public Thumb[] scrapeExtraFanart() {
        return new Thumb[0];
    }

    @Nonnull
    @Override
    public MPAARating scrapeMPAA() {
        return MPAARating.RATING_XXX;
    }

    @Nonnull
    @Override
    public ID scrapeID() {
        String keyword = (scrapingLanguage == Language.ENGLISH) ? "Code:" : "品番:";
        if(movie_data.containsKey(keyword)){
            Element code = movie_data.get(keyword);
            if(code != null){
                return new ID(code.text());
            }
        }
        return ID.BLANK_ID;
    }

    @Nonnull
    @Override
    public ArrayList<Genre> scrapeGenres() {
        ArrayList<Genre> genres = new ArrayList<>();
        String keyword = (scrapingLanguage == Language.ENGLISH) ? "Genre:" : "ジャンル:";
        if(movie_data.containsKey(keyword)){
            for(Element genre : movie_data.get(keyword).getElementsByTag("a")){
                genres.add(new Genre(genre.text()));
            }
        }
        return genres;
    }

    @Nonnull
    @Override
    public ArrayList<Actor> scrapeActors() {
        ArrayList<Actor> actors = new ArrayList<>();
        String actressKeyword = (scrapingLanguage == Language.ENGLISH) ? "Actress:" : "女優:";
        if(movie_data.containsKey(actressKeyword)){
            for(Element actress : movie_data.get(actressKeyword).getElementsByTag("a")){
                actors.add(new Actor(actress.text(), "", null));
            }
        }

        String actorKeyword = (scrapingLanguage == Language.ENGLISH) ? "Actor:" : "俳優:";
        if(movie_data.containsKey(actorKeyword)){
            for(Element actor : movie_data.get(actorKeyword).getElementsByTag("a")){
                actors.add(new Actor(actor.text(), "", null));
            }
        }
        return actors;
    }

    @Nonnull
    @Override
    public ArrayList<Director> scrapeDirectors() {
        ArrayList<Director> directors = new ArrayList<>();
        String keyword = (scrapingLanguage == Language.ENGLISH) ? "Director:" : "監督:";
        if(movie_data.containsKey(keyword)){
            Element directorElement = movie_data.get(keyword);
            if(directorElement != null){
                directors.add(new Director(directorElement.text(), null));
            }
        }
        return directors;
    }

    @Nonnull
    @Override
    public Studio scrapeStudio() {
        String keyword = (scrapingLanguage == Language.ENGLISH) ? "Maker:" : "メーカー:";
        if(movie_data.containsKey(keyword)){
            Element maker = movie_data.get(keyword);
            if(maker != null){
                return new Studio(maker.text());
            }
        }
        String labelKeyword = (scrapingLanguage == Language.ENGLISH) ? "Label:" : "レーベル:";
        if(movie_data.containsKey(labelKeyword)){
            Element label = movie_data.get(labelKeyword);
            if(label != null){
                return new Studio(label.text());
            }
        }
        return Studio.BLANK_STUDIO;
    }

    @Nonnull
    @Override
    public ArrayList<Tag> scrapeTags() {
        ArrayList<Tag> tags = new ArrayList<>();
        String keyword = (scrapingLanguage == Language.ENGLISH) ? "Tags:" : "タグ:";
        if(movie_data.containsKey(keyword)){
            for(Element tag : movie_data.get(keyword).getElementsByTag("a")){
                tags.add(new Tag(tag.text()));
            }
        }
        return tags;
    }

    @Nonnull
    @Override
    public String createSearchString(File file) {
        scrapedMovieFile = file;
        return createSearchStringFromId(findIDTagFromFile(file, false));
    }

    @Nonnull
    @Override
    public String createSearchString2(File file) {
        scrapedMovieFile = file;
        return createSearchStringFromId2(findIDTagFromFile(file, false));
    }

    @Override
    public String createSearchStringFromId(String id) {
        this.id = id;
        return "https://missav.ws/dm32/" + getUrlLanguageToUse() + "/" + id;
    }

    @Override
    public String createSearchStringFromId2(String id) {
        this.id = id;
        return "https://missav.ai/dm32/" + getUrlLanguageToUse() + "/" + id;
    }

    @Override
    public SearchResult[] getSearchResults(String searchString) throws IOException {
        var result = new SearchResult(searchString);
        return new SearchResult[]{result};
    }

    @Override
    public SiteParsingProfile newInstance() {
        return new MissAVParsingProfile();
    }

    @Override
    public String getParserName() {
        return "MissAV";
    }
}
