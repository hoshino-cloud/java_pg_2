import com.sun.net.httpserver.HttpServer; // Webサーバー機能を読み込みます
import java.net.InetSocketAddress; // 待ち受ける番号（ポート）を指定する機能を読み込みます
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

public class App { // Appという名前のプログラム本体です
    public static void main(String[] args) throws Exception { // プログラムを開始する場所です
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0); // 8080番で待ち受けるサーバーを用意します
        server.createContext("/", exchange -> { // ブラウザから「/」に来たときの処理です
            String path = exchange.getRequestURI().getPath(); // アクセスされたパスを取り出します
            String message;
            if (path.equals("/hello")) { // パスが /hello か比べます
                String query = exchange.getRequestURI().getRawQuery(); // 追加: URLのクエリ（?より後の部分）を取り出します
                System.out.println("query = " + query);
                String name = query == null ? "ゲスト" : URLDecoder.decode(query.substring(5), StandardCharsets.UTF_8); // 追加: %で始まるURLエンコードを日本語へ復元します
                message = "こんにちは、" + name + "さん！"; // 追加: 取り出した名前を応答に入れます
            } else if (path.equals("/bye")) { // パスが /bye か比べます
                message = "さようなら！";
            } else if (path.equals("/hi")) { // パスが /bye か比べます
                message = "ハイ！";
            } else {
                message = "ページが見つかりません";
            }
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8"); // 文字をUTF-8（日本語が文字化けしにくい形式）で返すと伝えます
            byte[] body = message.getBytes("UTF-8"); // 返す文字を送信できるデータに変換します
            exchange.sendResponseHeaders(200, body.length); // 成功を表す200とデータの長さを送ります
            exchange.getResponseBody().write(body); // 文字のデータをブラウザへ送ります
            exchange.getResponseBody().close(); // 送信を完了します
        }); // 「/」への処理を登録します
        server.start(); // サーバーを起動します
        System.out.println("サーバー起動: http://localhost:8080 （止めるときは Ctrl+C）"); // 起動した場所をターミナルに表示します
    } // mainの処理を終えます
} // Appの定義を終えます
