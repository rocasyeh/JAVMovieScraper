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

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AV123ParsingProfile extends SiteParsingProfile implements SpecificProfile {

	public static final String urlLanguageEnglish = "en";
	public static final String urlLanguageJapanese = "ja";

    final String titlePath = "h1.watch__title";
    final String posterPath = ".player";
    final String plotPath = "p.watch__desc-text";
    String movieDetailsPath = ".content .detail-item"; // kept for XStream backward compatibility
    final String watchInfoPath = "dl.watch__info";
    Map<String, Element> movie_data = new HashMap<>();
    String id, url;
    Document japaneseDocument;

    @Override
    public void prepareData(){
        Element message = document.select(".message").first();
        if(message != null){
            System.err.println(message.text());
        } else {
            Element infoList = document.selectFirst(watchInfoPath);
            if (infoList != null) {
                for (Element row : infoList.select(".watch__info-row")) {
                    Element dt = row.selectFirst("dt");
                    Element dd = row.selectFirst("dd");
                    if (dt != null && dd != null) {
                        movie_data.put(normalizeKey(dt.text()), dd);
                    }
                }
            }
        }
    }

    private static final Pattern BACKGROUND_IMAGE_STYLE_PATTERN = Pattern.compile("background-image\\s*:\\s*url\\(['\"]?(.*?)['\"]?\\)");

    private void initializeJapaneseDocument(){
        try {
            // TODO: FIX this hack
            // Replace the original url since just a simple string concatenation of "https://123av.com/ja/v" might just redirect to the english page
            var response = downloadDocumentFromUrl(url.replace("https://123av.com/en", "https://123av.com/ja")).bufferUp();
            if (response.statusCode() == 200) {
                japaneseDocument = response.parse();
            }
        }catch (IOException e){
            System.err.println(e.getMessage());
        }

    }

    private String extractPosterUrlFromStyle(String style) {
        if (style == null) {
            return null;
        }
        Matcher matcher = BACKGROUND_IMAGE_STYLE_PATTERN.matcher(style);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private String normalizeKey(String key) {
        if (key == null) {
            return null;
        }
        return key.trim().replaceAll("[:：]$", "");
    }

    @Override
    public Connection.Response downloadDocumentFromUrl(String url){
        try {
            var response = Jsoup.connect(url).userAgent(UserAgent.getRandomUserAgent()).followRedirects(true).ignoreHttpErrors(true).timeout(CONNECTION_TIMEOUT_VALUE).execute();
            if(response.statusCode() == 200){

                response = response.bufferUp();
                var continueButton = response.parse().selectFirst("a.btn-primary");
                if(continueButton != null && continueButton.text().equals("Click here to continue")) {
                    String redirectUrl = continueButton.attr("href");

                    System.out.println("AV123: Redirecting to: " + redirectUrl);
                    this.url = redirectUrl;
                    return Jsoup.connect(redirectUrl).userAgent(UserAgent.getRandomUserAgent()).followRedirects(true).ignoreHttpErrors(true).timeout(CONNECTION_TIMEOUT_VALUE).execute();
                }
            }
            this.url = response.url().toString();
            return response;
        }catch (IOException e){
            System.err.println(e.getMessage());
        }
        return null;
    }

    @Nonnull
    @Override
    public Title scrapeTitle() {
        return new Title(document.select(titlePath).text());
    }

    @Nonnull
    @Override
    public OriginalTitle scrapeOriginalTitle() {
        initializeJapaneseDocument();
        if(japaneseDocument != null){
            return new OriginalTitle(japaneseDocument.select(titlePath).text());
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
        String keyword = (scrapingLanguage == Language.ENGLISH)? "Series" : "シリーズ";
        if(movie_data.containsKey(keyword)){
            return new Set(movie_data.get(keyword).text());
        }
        return Set.BLANK_SET;
    }

    @Nonnull
    @Override
    public Rating scrapeRating() {
		// this site does not have ratings, so just return some default values
        return Rating.BLANK_RATING;
    }

    @Nonnull
    @Override
    public ReleaseDate scrapeReleaseDate() {
        String keyword = (scrapingLanguage == Language.ENGLISH)? "Release date" : "発売日";
        if(movie_data.containsKey(keyword)){
            return new ReleaseDate(movie_data.get(keyword).text());
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
		// This type of info doesn't exist on AV123
        return Top250.BLANK_TOP250;
    }

    @Nonnull
    @Override
    public Votes scrapeVotes() {
		// This type of info doesn't exist on AV123
        return Votes.BLANK_VOTES;
    }

    @Nonnull
    @Override
    public Outline scrapeOutline() {
		// This type of info doesn't exist on AV123
        return Outline.BLANK_OUTLINE;
    }

    @Nonnull
    @Override
    public Plot scrapePlot() {
        Element plotElement = document.select(plotPath).first();
        if(plotElement != null){
            return new Plot(plotElement.text());
        }
        return Plot.BLANK_PLOT;
    }

    @Nonnull
    @Override
    public Tagline scrapeTagline() {
		// This type of info doesn't exist on AV123
        return Tagline.BLANK_TAGLINE;
    }

    @Nonnull
    @Override
    public Runtime scrapeRuntime() {
        String keyword = (scrapingLanguage == Language.ENGLISH)? "Duration" : "再生時間";
        if(movie_data.containsKey(keyword)){
            Element durationElement = movie_data.get(keyword);
            if (durationElement != null) {
                return new Runtime(durationElement.text());
            }
        }
        return Runtime.BLANK_RUNTIME;
    }

    @Override
    public Thumb[] scrapePosters(boolean cropPosters) {
        List<Thumb> posters = new ArrayList<>();
        try {
            Element poster = document.select(posterPath).first();
            if (poster != null) {
                String posterUrl = extractPosterUrlFromStyle(poster.attr("style"));
                if (posterUrl == null || posterUrl.isBlank()) {
                    posterUrl = poster.attr("data-poster");
                }
                if (posterUrl != null && !posterUrl.isBlank()) {
                    posters.add(new Thumb(posterUrl, cropPosters));
                }
            }
        }catch (IOException e){
            System.err.println(e.getMessage());
        }
        return posters.toArray(new Thumb[0]);
    }

    @Override
    public Thumb[] scrapeFanart() {
        List<Thumb> posters = new ArrayList<>();
        try {
            Element poster = document.select(posterPath).first();
            if (poster != null) {
                String posterUrl = extractPosterUrlFromStyle(poster.attr("style"));
                if (posterUrl == null || posterUrl.isBlank()) {
                    posterUrl = poster.attr("data-poster");
                }
                if (posterUrl != null && !posterUrl.isBlank()) {
                    posters.add(new Thumb(posterUrl));
                }
            }
        }catch (IOException e){
            System.err.println(e.getMessage());
        }
        return posters.toArray(new Thumb[0]);
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
        String keyword = (scrapingLanguage == Language.ENGLISH)? "Code" : "コード";
        if(movie_data.containsKey(keyword)){
            return new ID(movie_data.get(keyword).text());
        }
        return ID.BLANK_ID;
    }

    private ArrayList<String> collectChipTexts(Element element) {
        ArrayList<String> values = new ArrayList<>();
        if (element == null) {
            return values;
        }
        for (Element chip : element.select("a.chip")) {
            if (chip != null && !chip.text().isBlank()) {
                values.add(chip.text().trim());
            }
        }
        if (values.isEmpty()) {
            String text = element.text().trim();
            if (!text.isEmpty()) {
                values.add(text);
            }
        }
        return values;
    }

    @Nonnull
    @Override
    public ArrayList<Genre> scrapeGenres() {
        ArrayList<Genre> genres = new ArrayList<>();
        String keyword = (scrapingLanguage == Language.ENGLISH)? "Genres" : "ジャンル";
        if(movie_data.containsKey(keyword)){
            for(String genreText : collectChipTexts(movie_data.get(keyword))){
                genres.add(new Genre(genreText));
            }
        }
        return genres;
    }

    @Nonnull
    @Override
    public ArrayList<Actor> scrapeActors() {
        ArrayList<Actor> actresses = new ArrayList<>();
        String keyword = (scrapingLanguage == Language.ENGLISH)? "Cast" : "出演者";
        if(movie_data.containsKey(keyword)){
            for(String actressText : collectChipTexts(movie_data.get(keyword))){
               actresses.add(new Actor(actressText, null, null));
            }
        } else if (scrapingLanguage == Language.ENGLISH && movie_data.containsKey("Actresses")) {
            for(String actressText : collectChipTexts(movie_data.get("Actresses"))){
               actresses.add(new Actor(actressText, null, null));
            }
        }
        return actresses;
    }

    @Nonnull
    @Override
    public ArrayList<Director> scrapeDirectors() {
		// This type of info doesn't exist on AV123
        return new ArrayList<>();
    }

    @Nonnull
    @Override
    public Studio scrapeStudio() {
        String keyword = (scrapingLanguage == Language.ENGLISH)? "Maker" : "メーカー";
        if(movie_data.containsKey(keyword)){
            return new Studio(movie_data.get(keyword).text());
        } else {
            keyword = (scrapingLanguage == Language.ENGLISH)? "Label" : "ラベル";
            if (movie_data.containsKey(keyword)) {
                return new Studio(movie_data.get(keyword).text());
            }
        }
        return Studio.BLANK_STUDIO;
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
        return "https://123av.com/" + getUrlLanguageToUse() + "/v/" + id;
    }
    @Override
    public String createSearchStringFromId2(String id) {
        this.id = id;
        return "https://123av.com/" + getUrlLanguageToUse() + "/dm2/v/" + id;
    }

	private String getUrlLanguageToUse() {
		String urlLanguageToUse = (scrapingLanguage == Language.ENGLISH) ? urlLanguageEnglish : urlLanguageJapanese;
		return urlLanguageToUse;
	}

    @Override
    public SearchResult[] getSearchResults(String searchString) throws IOException {
        var result = new SearchResult(searchString);
        return new SearchResult[]{result};
    }

    @Override
    public SiteParsingProfile newInstance() {
        return new AV123ParsingProfile();
    }

    @Override
    public String getParserName() {
        return "123AV";
    }

    @Nonnull
    @Override
    public ArrayList<Tag> scrapeTags(){
        ArrayList<Tag> tags = new ArrayList<>();
        String keyword = (scrapingLanguage == Language.ENGLISH)? "Tags" : "タグ";
        if(movie_data.containsKey(keyword)){
            for(Element tag : movie_data.get(keyword).select("a.chip")){
                tags.add(new Tag(tag.text()));
            }
        }
        return tags;
    }
}
