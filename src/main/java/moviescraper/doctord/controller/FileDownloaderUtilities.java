package moviescraper.doctord.controller;

import java.awt.Image;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.awt.image.BufferedImage;

import javax.imageio.ImageIO;

import org.apache.commons.io.FileUtils;

import moviescraper.doctord.model.dataitem.Thumb;

/**
 * Wrapper class around standard methods to download images from urls or write a url to a file
 * so that set up a custom connection that allows us to set a user agent, etc.
 * This is necessary because some servers demand a user agent to download from them or a 403 error will be encountered.
 */
public class FileDownloaderUtilities {
	private static final String DEFAULT_USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_7_5) AppleWebKit/537.31 (KHTML, like Gecko) Chrome/26.0.1410.65 Safari/537.31";
	private static final int DEFAULT_CONNECT_TIMEOUT_MS = 10_000; // 10s
	private static final int DEFAULT_READ_TIMEOUT_MS = 20_000; // 20s
	private static final int MAX_RETRIES = 3;
	private static final long RETRY_BASE_DELAY_MS = 1000; // 1s base backoff

	/**
	 * Open a configured HttpURLConnection with sensible headers and timeouts.
	 */
	private static java.net.HttpURLConnection openHttpConnection(URL url, URL referrer) throws IOException {
		java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
		conn.setConnectTimeout(DEFAULT_CONNECT_TIMEOUT_MS);
		conn.setReadTimeout(DEFAULT_READ_TIMEOUT_MS);
		conn.setRequestProperty("User-Agent", DEFAULT_USER_AGENT);
		conn.setRequestProperty("Accept", "image/*,*/*;q=0.8");
		if (referrer != null) {
			conn.setRequestProperty("Referer", referrer.toString());
		}
		conn.setInstanceFollowRedirects(true);
		return conn;
	}

	public static Image getImageFromUrl(URL url) throws IOException {
		return getImageFromUrl(url, null);
	}

	public static Image getImageFromUrl(URL url, URL viewerURL) throws IOException {
		int attempt = 0;
		while (true) {
			attempt++;
			java.net.HttpURLConnection conn = openHttpConnection(url, viewerURL);
			int code = -1;
			try {
				code = conn.getResponseCode();
				if (code == java.net.HttpURLConnection.HTTP_OK) {
					try (InputStream inputStreamToUse = conn.getInputStream()) {
						Image imageFromUrl = ImageIO.read(inputStreamToUse);
						return imageFromUrl;
					}
				} else if ((code >= 500 && code < 600) || code == 429) {
					if (attempt >= MAX_RETRIES) {
						throw new IOException("Server returned HTTP response code: " + code + " for URL: " + url);
					}
					long delay = RETRY_BASE_DELAY_MS * (1 << (attempt - 1));
					try { Thread.sleep(delay); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); throw new IOException("Interrupted during retry backoff", ie); }
					continue; // retry
				} else {
					throw new IOException("Server returned HTTP response code: " + code + " for URL: " + url);
				}
			} catch (IOException e) {
				if (attempt >= MAX_RETRIES) throw e;
				long delay = RETRY_BASE_DELAY_MS * (1 << (attempt - 1));
				try { Thread.sleep(delay); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); throw new IOException("Interrupted during retry backoff", ie); }
			} finally {
				conn.disconnect();
			}
		}
	}

	public static Image getImageFromThumb(Thumb thumb) {
		if (thumb != null) {
			try {
				return getImageFromUrl(thumb.getThumbURL(), thumb.getReferrerURL());
			} catch (IOException ex) {
				ex.printStackTrace();
				return null;
			}
		}
		return null;
	}

	public static void writeURLToFile(URL url, File file) throws IOException {
		writeURLToFile(url, file, null);
	}

	public static void writeURLToFile(URL url, File file, URL viewerUrl) throws IOException {
		try {
			java.net.HttpURLConnection imageConnection = openHttpConnection(url, viewerUrl);
			try (InputStream in = imageConnection.getInputStream()) {
				FileUtils.copyInputStreamToFile(in, file);
			}

		} catch (Throwable t) {
			System.out.println("Cannot write file: " + t.getMessage());
		}

	}
}
