import com.sun.net.httpserver.HttpServer; // Webサーバー機能を読み込みます
import java.net.InetSocketAddress; // 待ち受ける番号（ポート）を指定する機能を読み込みます
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList; // 追加: Todoを並べて入れるための部品を読み込みます
import java.util.List; // 追加: Todoの一覧を扱うための部品を読み込みます

public class App { // Appという名前のプログラム本体です
    public static void main(String[] args) throws Exception { // プログラムを開始する場所です
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0); // 8080番で待ち受けるサーバーを用意します
        List<String> todos = new ArrayList<>();// 追加: Todoをためておく一覧を1回だけ作ります
        server.createContext("/", exchange -> { // ブラウザから「/」に来たときの処理です
            String path = exchange.getRequestURI().getPath(); // アクセスされたパスを取り出します
            String method = exchange.getRequestMethod(); // 追加: GETかPOSTかを取り出します
            String message;
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8"); // 追加:
                                                                                            // 基本の返す形式をUTF-8の通常テキストにします
            if (path.equals("/add") && method.equals("POST")) { // 追加: /addへのPOSTでTodoを追加します
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8); // 追加:
                                                                                                            // 送られた内容をUTF-8の文字列で受け取ります
                String value = body.substring(5); // 追加: "todo="の5文字を除きます
                String todo = URLDecoder.decode(value, StandardCharsets.UTF_8); // 追加: URL形式の日本語を元に戻します
                if (!todo.isEmpty()) { // 追加: 空の入力ではないかを確かめます
                    todos.add(todo); // 追加: Todoを一覧に追加します
                } // 追加: 空の入力を追加しない処理を終えます
                exchange.getResponseHeaders().set("Location", "/"); // 追加: 戻る先を/に指定します
                exchange.sendResponseHeaders(303, -1); // 追加: 303で/へ戻すよう伝えます
                exchange.close(); // 追加: 通信を閉じます
                return; // 追加: /addの処理をここで終えます
            } else if (path.equals("/")) { // 追加: パスが / の場合
                String html = "<form method='post' action='/add'><input name='todo'><button>追加</button></form><ul>"; // 追加:
                                                                                                                     // Todo入力フォームとHTMLのリストを始めます
                for (String todo : todos) { // 追加: Todoを1件ずつ取り出します
                    html += "<li>" + todo + "</li>"; // 追加: 1件をリストの行にします
                } // 追加: Todoを1件ずつ追加する処理を終えます
                html += "</ul>"; // 追加: HTMLのリストを閉じます
                message = html; // 追加: 組み立てたHTMLを返す内容にします
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8"); // 追加: /はHTML形式で返します
            } else if (path.equals("/hello")) { // パスが /hello か比べます
                String query = exchange.getRequestURI().getRawQuery(); // 追加: URLのクエリ（?より後の部分）を取り出します
                System.out.println("query = " + query);
                String name = query == null ? "ゲスト" : URLDecoder.decode(query.substring(5), StandardCharsets.UTF_8); // 追加:
                                                                                                                     // %で始まるURLエンコードを日本語へ復元します
                message = "こんにちは、" + name + "さん！"; // 追加: 取り出した名前を応答に入れます
            } else if (path.equals("/bye")) { // パスが /bye か比べます
                message = "さようなら！";
            } else if (path.equals("/hi")) { // パスが /bye か比べます
                message = "ハイ！";
            } else {
                message = "ページが見つかりません";
            }
            byte[] body = message.getBytes("UTF-8"); // 返す文字を送信できるデータに変換します
            exchange.sendResponseHeaders(200, body.length); // 成功を表す200とデータの長さを送ります
            exchange.getResponseBody().write(body); // 文字のデータをブラウザへ送ります
            exchange.getResponseBody().close(); // 送信を完了します
        }); // 「/」への処理を登録します
        server.start(); // サーバーを起動します
        System.out.println("サーバー起動: http://localhost:8080 （止めるときは Ctrl+C）"); // 起動した場所をターミナルに表示します
    } // mainの処理を終えます
} // Appの定義を終えます
