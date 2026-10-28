import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

// Todoの登録・表示・保存を行うWebアプリケーション本体です。
class TodoApplication {
    // SQLiteデータベースファイルへの接続先です。
    private static final String DB_URL = "jdbc:sqlite:todos.db";
    // 既存データを移行するために確認するCSVファイルです。
    private static final Path CSV_PATH = Path.of("todos.csv");

    // アプリケーションを起動し、HTTPの受付先を登録します。
    static void main(String[] args) throws Exception {
        createTable(); // DBのtodosテーブルを作成・更新します。
        migrateCsvIfPresent(); // CSVが残っていればDBへ移行します。
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0); // 8080番ポートでサーバーを作ります。
        server.createContext("/", TodoApplication::handleRequest); // 通常画面用のリクエスト処理を登録します。
        server.createContext("/api/todos", exchange -> { // Todo一覧をJSONで返す入口を追加する
            if (!exchange.getRequestMethod().equals("GET")) { // GET以外の要求を判定する
                exchange.sendResponseHeaders(405, -1); // Method Not Allowedを返す
                exchange.close(); // 接続を閉じる
                return; // 処理を終える
            } // GET以外の要求の処理を閉じる
            try { // データベース取得時の例外を扱う
                byte[] body = todosJson().getBytes(StandardCharsets.UTF_8); // JSON文字列をUTF-8の本文へ変換する
                exchange.getResponseHeaders().set("Content-Type", "application/json"); // charsetなしのJSON Content-Typeを設定する
                exchange.sendResponseHeaders(200, body.length); // 成功ステータスと本文サイズを送る
                exchange.getResponseBody().write(body); // JSON本文を書き出す
            } catch (SQLException e) { // データベースエラーを受け取る
                exchange.sendResponseHeaders(500, -1); // サーバーエラーを返す
            } finally { // 成否にかかわらず後処理を行う
                exchange.close(); // 接続を閉じる
            } // 後処理を閉じる
        }); // Todo一覧JSON用の入口を閉じる
        server.start(); // HTTPサーバーの受付を開始します。
        System.out.println("サーバー起動: http://localhost:8080 （終了するときは Ctrl+C）");
    }

    // 画面表示、Todoの追加・完了・削除の各リクエストを処理します。
    private static void handleRequest(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath(); // URLのパス部分を取得します。
        String message; // レスポンス本文を格納します。
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
        try {
            if (path.equals("/add") && exchange.getRequestMethod().equals("POST")) {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8); // フォーム本文をUTF-8文字列で読み取ります。
                String title = getFormValue(body, "todo"); // Todo名を取得します。
                String dueDate = getFormValue(body, "dueDate"); // 期限日を取得します。
                if (!title.isEmpty() && !dueDate.isEmpty()) addTodo(title, dueDate); // 両方入力済みならDBへ追加します。
                redirectHome(exchange); // 完了後は一覧へリダイレクトします。
                return;
            }
            if (path.equals("/done")) {
                Integer id = getId(exchange.getRequestURI().getQuery()); // URLのidパラメータを数値へ変換します。
                if (id != null) markDone(id); // 対象Todoを完了状態にします。
                redirectHome(exchange);
                return;
            }
            if (path.equals("/delete")) {
                Integer id = getId(exchange.getRequestURI().getQuery()); // URLのidパラメータを数値へ変換します。
                if (id != null) deleteTodo(id); // 対象Todoを削除します。
                redirectHome(exchange);
                return;
            }
            message = path.equals("/") ? renderTodoList() : "ページが見つかりません";
            if (path.equals("/")) exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        } catch (SQLException e) {
            message = "データベースエラー: " + e.getMessage();
        }
        byte[] body = message.getBytes(StandardCharsets.UTF_8); // 本文を送信用バイト列に変換します。
        exchange.sendResponseHeaders(200, body.length); // 成功ステータスと本文サイズを送ります。
        exchange.getResponseBody().write(body); // 本文を送ります。
        exchange.close(); // 通信を終了します。
    }

    // 初回起動時にテーブルを作り、古いDBには期限日列を追加します。
    private static void createTable() throws SQLException {
        try (Connection c = DriverManager.getConnection(DB_URL); Statement s = c.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS todos (id INTEGER PRIMARY KEY, title TEXT NOT NULL, due_date TEXT NOT NULL DEFAULT '', done INTEGER NOT NULL)");
            boolean hasDueDate = false; // due_date列の存在を記録します。
            try (ResultSet columns = s.executeQuery("PRAGMA table_info(todos)")) {
                while (columns.next()) if ("due_date".equals(columns.getString("name"))) hasDueDate = true;
            }
            if (!hasDueDate) s.execute("ALTER TABLE todos ADD COLUMN due_date TEXT NOT NULL DEFAULT ''");
        }
    }

    // todos.csvがある場合に、既存のTodoをSQLiteへ移行します。
    private static void migrateCsvIfPresent() throws IOException, SQLException {
        if (!Files.exists(CSV_PATH)) return;
        try (Connection c = DriverManager.getConnection(DB_URL);
             PreparedStatement insert = c.prepareStatement("INSERT OR IGNORE INTO todos (id, title, due_date, done) VALUES (?, ?, ?, ?)");
             PreparedStatement fillDate = c.prepareStatement("UPDATE todos SET due_date = ? WHERE id = ? AND due_date = ''")) {
            for (String line : Files.readAllLines(CSV_PATH, StandardCharsets.UTF_8)) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] fields = line.split(",", 4);
                if (fields.length != 4) continue;
                try {
                    int id = Integer.parseInt(fields[0]);
                    int done = Integer.parseInt(fields[1]);
                    insert.setInt(1, id); insert.setString(2, fields[3]); insert.setString(3, fields[2]); insert.setInt(4, done);
                    insert.executeUpdate();
                    fillDate.setString(1, fields[2]); fillDate.setInt(2, id); fillDate.executeUpdate();
                } catch (NumberFormatException ignored) { }
            }
        }
    }

    // 新しいTodoを未完了としてDBへ保存します。
    private static void addTodo(String title, String dueDate) throws SQLException {
        try (Connection c = DriverManager.getConnection(DB_URL);
             PreparedStatement s = c.prepareStatement("INSERT INTO todos (title, due_date, done) VALUES (?, ?, 0)")) {
            s.setString(1, title); s.setString(2, dueDate); s.executeUpdate();
        }
    }

    // 指定IDのTodoを完了状態へ更新します。
    private static void markDone(int id) throws SQLException { update("UPDATE todos SET done = 1 WHERE id = ?", id); }
    // 指定IDのTodoをDBから削除します。
    private static void deleteTodo(int id) throws SQLException { update("DELETE FROM todos WHERE id = ?", id); }
    // IDを1つ受け取るUPDATEまたはDELETEを共通処理します。
    private static void update(String sql, int id) throws SQLException {
        try (Connection c = DriverManager.getConnection(DB_URL); PreparedStatement s = c.prepareStatement(sql)) {
            s.setInt(1, id); s.executeUpdate();
        }
    }

    // Todoを期限日、続いてIDの昇順で取得します。
    private static List<TodoItem> listTodos() throws SQLException {
        List<TodoItem> todos = new ArrayList<>();
        try (Connection c = DriverManager.getConnection(DB_URL);
             PreparedStatement s = c.prepareStatement("SELECT id, title, due_date, done FROM todos ORDER BY due_date ASC, id ASC");
             ResultSet r = s.executeQuery()) {
            while (r.next()) todos.add(new TodoItem(r.getInt("id"), r.getString("title"), r.getString("due_date"), r.getInt("done") != 0));
        }
        return todos;
    }

    // 一覧画面として返すHTML文字列を組み立てます。
    private static String renderTodoList() throws SQLException {
        StringBuilder html = new StringBuilder("<!DOCTYPE html><html><head><meta charset='UTF-8'><title>やることリスト</title><style>body{max-width:640px;margin:24px auto;padding:0 16px;font-size:16px}input,button{font-size:16px}.due-date{margin-left:2em}</style></head><body><h1>やることリスト</h1><form method='post' action='/add'><input name='todo' placeholder='やること' required><label class='due-date'>締め切り日 <input type='date' name='dueDate' required></label> <button>追加</button></form><ul>");
        for (TodoItem todo : listTodos()) {
            String title = escapeHtml(todo.title);
            if (todo.done) title = "<span style='color:blue;text-decoration:line-through'>" + title + "</span>";
            html.append("<li>").append(title).append("　　締め切り日: ").append(escapeHtml(todo.dueDate))
                    .append(" <a href='/done?id=").append(todo.id).append("'>完了</a> <a href='/delete?id=").append(todo.id).append("'>削除</a></li>");
        }
        return html.append("</ul></body></html>").toString();
    }

    // 303リダイレクトで一覧画面へ戻します。
    private static void redirectHome(HttpExchange e) throws IOException { e.getResponseHeaders().set("Location", "/"); e.sendResponseHeaders(303, -1); e.close(); }
    // クエリ文字列のid=数字から数値IDを取り出します。
    private static Integer getId(String q) { try { return q != null && q.startsWith("id=") ? Integer.parseInt(q.substring(3)) : null; } catch (NumberFormatException e) { return null; } }
    // フォーム本文から指定された項目の値をURLデコードして取り出します。
    private static String getFormValue(String body, String key) {
        for (String pair : body.split("&")) { int i = pair.indexOf('='); if (i >= 0 && URLDecoder.decode(pair.substring(0, i), StandardCharsets.UTF_8).equals(key)) return URLDecoder.decode(pair.substring(i + 1), StandardCharsets.UTF_8); }
        return "";
    }
    // HTMLの特殊文字をエスケープし、入力値を安全に画面表示します。
    private static String escapeHtml(String value) { return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;"); }

    private static String todosJson() throws SQLException { // Todo一覧をJSON配列へ変換する
        StringBuilder json = new StringBuilder("["); // JSON配列の開始文字を用意する
        for (TodoItem todo : listTodos()) { // 全Todoを順に処理する
            if (json.length() > 1) json.append(","); // 2件目以降の前に区切り文字を付ける
            json.append("{\"title\":\"").append(escapeJson(todo.title())).append("\",\"done\":").append(todo.done()).append("}"); // タイトルと完了状態をJSONオブジェクトとして追加する
        } // Todoごとの処理を閉じる
        return json.append("]").toString(); // JSON配列を閉じて返す
    } // JSON配列作成メソッドを閉じる

    private static String escapeJson(String value) { // JSON文字列として安全な表現へ変換する
        StringBuilder escaped = new StringBuilder(); // エスケープ後の文字列を用意する
        for (int i = 0; i < value.length(); i++) { // 文字列を1文字ずつ処理する
            char ch = value.charAt(i); // 現在の文字を取得する
            switch (ch) { // 文字に応じてJSON用の表現を選ぶ
                case '\"': escaped.append("\\\""); break; // ダブルクォートをエスケープする
                case '\\': escaped.append("\\\\"); break; // バックスラッシュをエスケープする
                case '\b': escaped.append("\\b"); break; // バックスペースをエスケープする
                case '\f': escaped.append("\\f"); break; // フォームフィードをエスケープする
                case '\n': escaped.append("\\n"); break; // 改行をエスケープする
                case '\r': escaped.append("\\r"); break; // 復帰をエスケープする
                case '\t': escaped.append("\\t"); break; // タブをエスケープする
                default: if (ch < 0x20) escaped.append(String.format("\\u%04x", (int) ch)); else escaped.append(ch); // その他の制御文字をUnicodeエスケープし通常文字はそのまま追加する
            } // 分岐を閉じる
        } // 文字ごとの処理を閉じる
        return escaped.toString(); // エスケープ済み文字列を返す
    } // JSONエスケープ用メソッドを閉じる

    // Todoの1件分（ID、タイトル、期限日、完了状態）を表す不変データです。
    private record TodoItem(int id, String title, String dueDate, boolean done) { }
}
