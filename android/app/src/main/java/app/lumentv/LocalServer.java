package app.lumentv;

import android.content.res.AssetManager;
import android.net.Uri;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Answers WebView requests for {@link #ORIGIN}: serves the web app from
 * assets/www and implements the /proxy/ endpoint (mirrors server/proxy.mjs).
 */
final class LocalServer {
    static final String ORIGIN = "https://lumentv.app.local";

    private static final String UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36";
    private static final Pattern PROXY_PATH = Pattern.compile("^/proxy/(https?)/([^/]+)(.*)$");
    private static final Pattern URI_ATTR = Pattern.compile("URI=\"([^\"]+)\"");

    private final AssetManager assets;

    LocalServer(AssetManager assets) {
        this.assets = assets;
    }

    WebResourceResponse handle(WebResourceRequest req) {
        Uri url = req.getUrl();
        if (!url.toString().startsWith(ORIGIN)) return null; // let the WebView load it
        String path = url.getEncodedPath() == null ? "/" : url.getEncodedPath();
        try {
            if (path.startsWith("/proxy/")) return proxy(req, url, path);
            return asset(path);
        } catch (Exception e) {
            return text(502, "Bad Gateway", "Proxy error: " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------- assets

    private WebResourceResponse asset(String path) throws IOException {
        String p = Uri.decode(path).replaceAll("\\.\\.", "");
        if (p.equals("/") || p.isEmpty()) p = "/index.html";
        InputStream in;
        try {
            in = assets.open("www" + p);
        } catch (IOException e) {
            p = "/index.html"; // SPA fallback
            in = assets.open("www/index.html");
        }
        WebResourceResponse r = new WebResourceResponse(mime(p), p.endsWith(".html") || p.endsWith(".js") || p.endsWith(".css") ? "utf-8" : null, in);
        Map<String, String> h = new HashMap<>();
        h.put("Cache-Control", p.startsWith("/assets/") ? "public, max-age=31536000, immutable" : "no-cache");
        r.setResponseHeaders(h);
        return r;
    }

    private static String mime(String p) {
        String l = p.toLowerCase(Locale.ROOT);
        if (l.endsWith(".html")) return "text/html";
        if (l.endsWith(".js") || l.endsWith(".mjs")) return "text/javascript";
        if (l.endsWith(".css")) return "text/css";
        if (l.endsWith(".svg")) return "image/svg+xml";
        if (l.endsWith(".png")) return "image/png";
        if (l.endsWith(".json")) return "application/json";
        if (l.endsWith(".woff2")) return "font/woff2";
        return "application/octet-stream";
    }

    // ----------------------------------------------------------------- proxy

    private WebResourceResponse proxy(WebResourceRequest req, Uri url, String path) throws IOException {
        if ("OPTIONS".equals(req.getMethod())) return text(204, "No Content", "");
        if (path.equals("/proxy/health")) return withCors(new WebResourceResponse("application/json", "utf-8", 200, "OK", new HashMap<>(), stream("{\"ok\":true}")));

        Matcher m = PROXY_PATH.matcher(path);
        if (!m.matches()) return text(400, "Bad Request", "Bad proxy URL");
        String target = m.group(1) + "://" + m.group(2) + (m.group(3).isEmpty() ? "/" : m.group(3));
        if (url.getEncodedQuery() != null) target += "?" + url.getEncodedQuery();

        HttpURLConnection conn = open(target, req.getRequestHeaders().get("Range"));
        int status = conn.getResponseCode();
        String finalUrl = conn.getURL().toString();
        String type = conn.getContentType() == null ? "application/octet-stream" : conn.getContentType();
        InputStream body = status >= 400 ? conn.getErrorStream() : conn.getInputStream();
        if (body == null) body = stream("");

        Map<String, String> headers = new HashMap<>();
        for (String h : new String[]{"Cache-Control", "Last-Modified", "ETag", "Accept-Ranges", "Content-Range"}) {
            String v = conn.getHeaderField(h);
            if (v != null) headers.put(h, v);
        }

        String mime = type.split(";")[0].trim();
        boolean manifest = type.toLowerCase(Locale.ROOT).contains("mpegurl")
                || Uri.parse(finalUrl).getPath() != null && Uri.parse(finalUrl).getPath().toLowerCase(Locale.ROOT).endsWith(".m3u8");
        if (manifest && status < 400) {
            String text = new String(readAll(body), StandardCharsets.UTF_8);
            if (text.contains("#EXT-X-")) {
                text = rewriteManifest(text, finalUrl);
                mime = "application/vnd.apple.mpegurl";
            }
            body = stream(text);
        }
        String reason = conn.getResponseMessage();
        if (reason == null || reason.isEmpty()) reason = status < 400 ? "OK" : "Error";
        return withCors(new WebResourceResponse(mime, null, status, reason, headers, body));
    }

    /** Opens a connection, following redirects (incl. http <-> https, which HttpURLConnection won't). */
    private static HttpURLConnection open(String target, String range) throws IOException {
        String current = target;
        for (int i = 0; i < 6; i++) {
            HttpURLConnection c = (HttpURLConnection) new URL(current).openConnection();
            c.setInstanceFollowRedirects(false);
            c.setConnectTimeout(15000);
            c.setReadTimeout(30000);
            c.setRequestProperty("User-Agent", UA);
            c.setRequestProperty("Accept", "*/*");
            if (range != null) c.setRequestProperty("Range", range);
            int code = c.getResponseCode();
            if (code >= 300 && code < 400 && c.getHeaderField("Location") != null) {
                current = URI.create(current).resolve(c.getHeaderField("Location")).toString();
                c.disconnect();
                continue;
            }
            return c;
        }
        throw new IOException("Too many redirects");
    }

    static String toProxyPath(String absolute) {
        Uri u = Uri.parse(absolute);
        String q = u.getEncodedQuery();
        return ORIGIN + "/proxy/" + u.getScheme() + "/" + u.getEncodedAuthority() + (u.getEncodedPath() == null ? "/" : u.getEncodedPath()) + (q != null ? "?" + q : "");
    }

    static String rewriteManifest(String text, String base) {
        URI baseUri = URI.create(base);
        StringBuilder out = new StringBuilder();
        for (String line : text.split("\r?\n")) {
            String t = line.trim();
            if (t.isEmpty()) {
                out.append(line);
            } else if (t.startsWith("#")) {
                Matcher m = URI_ATTR.matcher(line);
                StringBuffer sb = new StringBuffer();
                while (m.find()) m.appendReplacement(sb, Matcher.quoteReplacement("URI=\"" + abs(baseUri, m.group(1)) + "\""));
                m.appendTail(sb);
                out.append(sb);
            } else {
                out.append(abs(baseUri, t));
            }
            out.append('\n');
        }
        return out.toString();
    }

    private static String abs(URI base, String ref) {
        try {
            return toProxyPath(base.resolve(ref.replace(" ", "%20")).toString());
        } catch (Exception e) {
            return ref;
        }
    }

    // --------------------------------------------------------------- helpers

    private static WebResourceResponse withCors(WebResourceResponse r) {
        Map<String, String> h = r.getResponseHeaders() == null ? new HashMap<>() : new HashMap<>(r.getResponseHeaders());
        h.put("Access-Control-Allow-Origin", "*");
        h.put("Access-Control-Allow-Headers", "*");
        h.put("Access-Control-Expose-Headers", "*");
        h.put("Access-Control-Allow-Methods", "GET, HEAD, OPTIONS");
        r.setResponseHeaders(h);
        return r;
    }

    private static WebResourceResponse text(int code, String reason, String body) {
        return withCors(new WebResourceResponse("text/plain", "utf-8", code, reason, new HashMap<>(), stream(body)));
    }

    private static InputStream stream(String s) {
        return new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        in.close();
        return bos.toByteArray();
    }
}
