import com.sun.net.httpserver.HttpServer; // Webサーバー機能を読み込みます
import java.net.InetSocketAddress; // 待ち受ける番号（ポート）を指定する機能を読み込みます
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList; // 追加: Todoを並べて入れるための部品を読み込みます
import java.util.List; // 追加: Todoの一覧を扱うための部品を読み込みます

public class App { // Appという名前のプログラム本体です
    static List<Todo> todos = new ArrayList<>(); // ★変更: Todoをためる一覧をクラス全体で使えるように作ります
    static int nextId = 1; // ★変更: 次にTodoへ付ける番号を1から始めます

    public static void main(String[] args) throws Exception { // プログラムを開始する場所です
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0); // 8080番で待ち受けるサーバーを用意します
        todos.add(new Todo(nextId++, "牛乳を買う")); // ★変更: 番号1の「牛乳を買う」を一覧へ入れます
        Todo egg = new Todo(nextId++, "卵を買う"); // ★変更: 番号2の「卵を買う」をeggに作ります
        egg.setDone(true); // ★変更: eggを完了済みにします
        todos.add(egg); // ★変更: 完了済みにしたeggを一覧へ入れます
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
                String title = URLDecoder.decode(value, StandardCharsets.UTF_8); // ★変更: フォームで受け取ったやることをtitleへ入れます
                if (!title.isEmpty()) { // ★変更: titleが空でないときだけ追加します
                    todos.add(new Todo(nextId, title)); // ★変更: 次の番号とtitleでTodoを1件追加します
                    nextId++; // ★変更: 次に使う番号を1つ増やします
                } // 追加: 空の入力を追加しない処理を終えます
                exchange.getResponseHeaders().set("Location", "/"); // 追加: 戻る先を/に指定します
                exchange.sendResponseHeaders(303, -1); // 追加: 303で/へ戻すよう伝えます
                exchange.close(); // 追加: 通信を閉じます
                return; // 追加: /addの処理をここで終えます
            } else if (path.equals("/done") && method.equals("GET")) { // ★追加: /doneへのGET（表示を求めるアクセス）を処理します
                String query = exchange.getRequestURI().getQuery(); // ★追加: URLの?以降にあるidの情報を受け取ります
                if (query != null && query.startsWith("id=") && query.length() > 3) { // ★追加: idが空でない形で付いているかを確かめます
                    try { // ★追加: 数字への変換でエラーが起きる場合に備えます
                        int id = Integer.parseInt(query.substring(3)); // ★追加: id=の後ろの文字を数字に変えます
                        for (Todo todo : todos) { // ★追加: Todoを1件ずつ確認します
                            if (todo.getId() == id) { // ★追加: Todoの番号が受け取ったidと同じかを確かめます
                                todo.setDone(true); // ★追加: 一致したTodoを完了済みにします
                                break; // ★追加: 一致した1件を処理したので繰り返しを終えます
                            } // ★追加: 番号が同じかの確認を終えます
                        } // ★追加: Todoを1件ずつ確認する処理を終えます
                    } catch (NumberFormatException e) { // ★追加: idが数字でない場合は何もしません
                    } // ★追加: 数字でないidの処理を終えます
                } // ★追加: idの形式の確認を終えます
                exchange.getResponseHeaders().set("Location", "/"); // ★追加: 戻る先を一覧の/に指定します
                exchange.sendResponseHeaders(303, -1); // ★追加: 303で一覧へ戻すよう伝えます
                exchange.close(); // ★追加: 通信を閉じます
                return; // ★追加: /doneの処理をここで終えます
            } else if (path.equals("/delete") && method.equals("GET")) { // ★追加: /deleteへのGET（表示を求めるアクセス）を処理します
                String query = exchange.getRequestURI().getQuery(); // ★追加: URLの?以降にあるidの情報を受け取ります
                if (query != null && query.startsWith("id=") && query.length() > 3) { // ★追加: idが空でない形で付いているかを確かめます
                    try { // ★追加: 数字への変換でエラーが起きる場合に備えます
                        int id = Integer.parseInt(query.substring(3)); // ★追加: id=の後ろの文字を数字に変えます
                        todos.removeIf(todo -> todo.getId() == id); // ★追加: 番号が同じTodoを一覧から取り除きます
                    } catch (NumberFormatException e) { // ★追加: idが数字でない場合は何もしません
                    } // ★追加: 数字でないidの処理を終えます
                } // ★追加: idの形式の確認を終えます
                exchange.getResponseHeaders().set("Location", "/"); // ★追加: 戻る先を一覧の/に指定します
                exchange.sendResponseHeaders(303, -1); // ★追加: 303で一覧へ戻すよう伝えます
                exchange.close(); // ★追加: 通信を閉じます
                return; // ★追加: /deleteの処理をここで終えます
            } else if (path.equals("/")) { // 追加: パスが / の場合
                String html = "<!DOCTYPE html><html><head><meta charset='UTF-8'><title>やることリスト</title><style>body { max-width: 640px; margin: 24px auto; padding: 0 16px; font-size: 16px; } h1 { font-size: 24px; } input, button { font-size: 16px; }</style></head><body><h1>やることリスト</h1><form method='post' action='/add'><input name='todo'><button>追加</button></form><ul>"; // 追加:
                if (todos.isEmpty()) {
                    html += "<li>やることは、いま0件です</li>";
                }
                                                                                                                     // Todo入力フォームとHTMLのリストを始めます
                for (Todo todo : todos) { // ★変更: 一覧のTodoを1件ずつ取り出します
                    String title = todo.getTitle(); // ★変更: Todoの内容を表示用に入れます
                    if (todo.isDone()) { // ★変更: Todoが完了済みかを確かめます
                        title = "<span style='color: blue; text-decoration: line-through;'>" + title + "</span>"; // ★変更: 完了済みの内容を青い打ち消し線で表示します
                    } // ★変更: 完了済みかを確かめる処理を終えます
                    html += "<li>" + title + " <a href='/done?id=" + todo.getId()
                            + "'>完了</a> <a href='/delete?id=" + todo.getId() + "'>削除</a></li>"; // ★追加:
                                                                                                // Todoの番号を付けた「完了」「削除」のリンクを一覧の行へ加えます
                } // 追加: Todoを1件ずつ追加する処理を終えます
                html += "</ul></body></html>"; // 追加: HTMLのリストを閉じます
                message = html; // 追加: 組み立てたHTMLを返す内容にします
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8"); // 追加: /はHTML形式で返します
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

class Todo { // ★変更: Todoを表すクラスを作ります
    private final int id; // ★変更: Todoの番号を入れます
    private final String title; // ★変更: やることの内容を入れます
    private boolean done; // ★変更: 完了済みかどうかを入れます

    Todo(int id, String title) { // ★変更: 番号とやることを受け取ってTodoを作ります
        this.id = id; // ★変更: 受け取った番号をidに入れます
        this.title = title; // ★変更: 受け取ったやることをtitleに入れます
        this.done = false; // ★変更: 新しいTodoは未完了にします
    } // ★変更: Todoを作る処理を終えます

    int getId() { // ★変更: Todoの番号を取り出すメソッドです
        return id; // ★変更: idの値を返します
    } // ★変更: 番号を取り出す処理を終えます

    String getTitle() { // ★変更: やることを取り出すメソッドです
        return title; // ★変更: titleの値を返します
    } // ★変更: やることを取り出す処理を終えます

    boolean isDone() { // ★変更: 完了済みかを取り出すメソッドです
        return done; // ★変更: doneの値を返します
    } // ★変更: 完了済みかを取り出す処理を終えます

    void setDone(boolean done) { // ★変更: 完了済みかを書き換えるメソッドです
        this.done = done; // ★変更: 受け取った完了状態をdoneに入れます
    } // ★変更: 完了状態を書き換える処理を終えます
} // ★変更: Todoクラスの定義を終えます
