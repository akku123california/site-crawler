package me.adeshina.sitecrawler;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import me.adeshina.sitecrawler.dto.WebPage;

import org.jsoup.Connection;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.UnsupportedMimeTypeException;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 * Core class responsible for actual crawling.
 */
final class CrawlWorker {

    private CrawlConfig config;
    private CrawlQueue queue;
    private String site;

    CrawlWorker(CrawlConfig config, CrawlQueue queue, String site) {
        this.config = config;
        this.queue = queue;
        this.site = site;
    }

    /**
     * Starts and co-ordinates the crawling process.
     */
    Set<WebPage> start() {

        int maxPages = config.maxPages();
        Set<WebPage> pages = new HashSet<>();
        queue.add(site);

        while (maxPages > 0 && !queue.isEmpty()) {
            Optional<String> nextUrl = queue.next();
            if (nextUrl.isPresent()) {
                try {
                    String url = nextUrl.get();
                    WebPage page = processPage(url);
                    pages.add(page);
                    maxPages--;

                    Thread.sleep(config.delayMilliSeconds());

                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        return pages;
    }

    /**
     * Download and clean HTML, convert to a WebPage, send all it's hyperlinks for queueing.
     */
    private WebPage processPage(String url) throws IOException {

        Connection.Response response;

        try {
            response = Jsoup.connect(url).userAgent(config.userAgent()).execute().bufferUp();

        } catch (MalformedURLException | HttpStatusException | UnsupportedMimeTypeException | SocketTimeoutException e) {
            throw new IOException(e);
        }

        Document page = response.parse();
        clean(page);
        page.select("a[href]").stream().map(link -> link.absUrl("href")).forEach(queue::add);
        queue.addToCrawled(url);

        return new WebPage(url, page.title(), page.html());
    }

    /**
     * Remove unwanted tags from HTML.
     */
    private void clean(Document page) {

        // clean body
        String[] tags = {"form", "style", "svg", "script", "canvas", "dialog", "embed", "figcaption"};
        Arrays.asList(tags).forEach(tag -> page.body().select(tag).remove());

        // clean head
        Arrays.asList("script", "style").forEach(tag -> {
            Elements element = page.head().select(tag);
            if (!element.isEmpty()) {
                element.remove();
            }
        });
    }

}
