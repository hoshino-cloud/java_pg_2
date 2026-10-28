/*
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection; // ★ SQLite接続を使う
import java.sql.DriverManager; // ★ SQLite接続を作る
import java.sql.PreparedStatement; // ★ SQLを安全に実行する
import java.sql.ResultSet; // ★ SELECT結果を読む
import java.sql.SQLException; // ★ データベース例外を扱う
import java.util.ArrayList;
import java.util.List;

public class App {
    static final String DB_URL = "jdbc:sqlite:todos.db"; // ★ CSVファイルではなくSQLiteデータベースを使う

    public static void main(String[] args) throws Exception {
        createTable(); // ★ 起動時にtodos表を用意する
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();
            String message;

            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
            try {
                if (path.equals("/add") && method.equals("POST")) {
                    String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                    String title = getFormValue(body, "todo");
                    if (!title.isEmpty()) {
                        addTodo(title); // ★ INSERTでTodoを追加する
                    }
                    redirectHome(exchange);
                    return;
                } else if (path.equals("/done") && method.equals("GET")) {
                    Integer id = getId(exchange.getRequestURI().getQuery());
                    if (id != null) {
                        markDone(id); // ★ UPDATEで完了状態を保存する
                    }
                    redirectHome(exchange);
                    return;
                } else if (path.equals("/delete") && method.equals("GET")) {
                    Integer id = getId(exchange.getRequestURI().getQuery());
                    if (id != null) {
                        deleteTodo(id); // ★ DELETEでTodoを削除する
                    }
                    redirectHome(exchange);
                    return;
                } else if (path.equals("/")) {
                    StringBuilder html = new StringBuilder("<!DOCTYPE html><html><head><meta charset='UTF-8'><title>やることリスト</title><style>body { max-width: 640px; margin: 24px auto; padding: 0 16px; font-size: 16px; } h1 { font-size: 24px; } input, button { font-size: 16px; }</style></head><body><h1>やることリスト</h1><form method='post' action='/add'><input name='todo' placeholder='やること' required> <button>追加</button></form><ul>");
                    List<Todo> todos = listTodos(); // ★ SELECTで一覧を読み出す
                    if (todos.isEmpty()) {
                        html.append("<li>やることは、ありません</li>");
                    }
                    for (Todo todo : todos) {
                        String title = escapeHtml(todo.title);
                        if (todo.done) {
                            title = "<span style='color: blue; text-decoration: line-through;'>" + title + "</span>";
                        }
                        html.append("<li>").append(title)
                            .append(" <a href='/done?id=").append(todo.id).append("'>完了</a>")
                            .append(" <a href='/delete?id=").append(todo.id).append("'>削除</a></li>");
                    }
                    html.append("</ul></body></html>");
                    message = html.toString();
                    exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                } else {
                    message = "ページが見つかりません";
                }
            } catch (SQLException e) {
                message = "データベースエラー: " + e.getMessage(); // ★ DB操作失敗を応答する
            }

            byte[] body = message.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        System.out.println("サーバー起動: http://localhost:8080 （終了するときは Ctrl+C）");
    }

    static void createTable() throws SQLException { // ★ SQLiteの表を初期化する
        String sql = "CREATE TABLE IF NOT EXISTS todos (id INTEGER PRIMARY KEY, title TEXT, done INTEGER)"; // ★ 必要な表定義
        try (Connection connection = DriverManager.getConnection(DB_URL); // ★ jdbc:sqlite:todos.dbへ接続する
             PreparedStatement statement = connection.prepareStatement(sql)) { // ★ SQLをPreparedStatementで実行する
            statement.execute();
        }
    }

    static void addTodo(String title) throws SQLException { // ★ INSERT処理
        String sql = "INSERT INTO todos (title, done) VALUES (?, ?)"; // ★ 追加SQL
        try (Connection connection = DriverManager.getConnection(DB_URL); // ★ SQLiteへ接続する
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, title);
            statement.setInt(2, 0);
            statement.executeUpdate(); // ★ INSERTを実行する
        }
    }

    static void markDone(int id) throws SQLException { // ★ UPDATE処理
        String sql = "UPDATE todos SET done = ? WHERE id = ?"; // ★ 完了状態を更新するSQL
        try (Connection connection = DriverManager.getConnection(DB_URL); // ★ SQLiteへ接続する
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, 1);
            statement.setInt(2, id);
            statement.executeUpdate(); // ★ UPDATEを実行する
        }
    }

    static void deleteTodo(int id) throws SQLException { // ★ DELETE処理
        String sql = "DELETE FROM todos WHERE id = ?"; // ★ 削除SQL
        try (Connection connection = DriverManager.getConnection(DB_URL); // ★ SQLiteへ接続する
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);
            statement.executeUpdate(); // ★ DELETEを実行する
        }
    }

    static List<Todo> listTodos() throws SQLException { // ★ SELECT処理
        List<Todo> todos = new ArrayList<>();
        String sql = "SELECT id, title, done FROM todos ORDER BY id"; // ★ 一覧取得SQL
        try (Connection connection = DriverManager.getConnection(DB_URL); // ★ SQLiteへ接続する
             PreparedStatement statement = connection.prepareStatement(sql); // ★ SELECTにもPreparedStatementを使う
             ResultSet result = statement.executeQuery()) { // ★ SELECTを実行して結果を読む
            while (result.next()) {
                todos.add(new Todo(result.getInt("id"), result.getString("title"), result.getInt("done") != 0)); // ★ 行をTodoへ変換する
            }
        }
        return todos;
    }

    static void redirectHome(HttpExchange exchange) throws java.io.IOException {
        exchange.getResponseHeaders().set("Location", "/");
        exchange.sendResponseHeaders(303, -1);
        exchange.close();
    }

    static Integer getId(String query) {
        if (query == null || !query.startsWith("id=") || query.length() <= 3) {
            return null;
        }
        try {
            return Integer.parseInt(query.substring(3));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static String getFormValue(String body, String key) {
        for (String pair : body.split("&")) {
            int equalsIndex = pair.indexOf('=');
            if (equalsIndex >= 0) {
                String name = URLDecoder.decode(pair.substring(0, equalsIndex), StandardCharsets.UTF_8);
                if (name.equals(key)) {
                    return URLDecoder.decode(pair.substring(equalsIndex + 1), StandardCharsets.UTF_8);
                }
            }
        }
        return "";
    }

    static String escapeHtml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
}

class Todo {
    final int id; // ★ DBから読み出したID
    final String title; // ★ DBから読み出したタイトル
    final boolean done; // ★ DBから読み出した完了状態

    Todo(int id, String title, boolean done) { // ★ SQLiteの列に合わせたコンストラクタ
        this.id = id;
        this.title = title;
        this.done = done;
    }
}
*/

public class App {
    public static void main(String[] args) throws Exception {
        TodoApplication.main(args);
    }
}
