import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;

public class Transaction {
    private double amount;
    private String category;
    private String type; // "income" или "expense"
    private LocalDateTime dateTime;

    public Transaction(double amount, String category, String type, LocalDateTime dateTime) {
        this.amount = amount;
        this.category = category;
        this.type = type;
        this.dateTime = dateTime;
    }

    public double getAmount() {
        return amount;
    }

    public String getCategory() {
        return category;
    }

    public String getType() {
        return type;
    }

    public LocalDateTime getDateTime() {
        return dateTime;
    }

    public static void resetUserData(long chatId) {
        try (Connection conn = Database.connect();
             PreparedStatement deleteTransactions = conn.prepareStatement("DELETE FROM transactions WHERE user_id = ?")) {

            deleteTransactions.setLong(1, chatId);
            deleteTransactions.executeUpdate();

            // Можно также очистить категории, если они сохраняются отдельно
            // PreparedStatement deleteCategories = conn.prepareStatement("DELETE FROM categories WHERE user_id = ?");
            // deleteCategories.setLong(1, chatId);
            // deleteCategories.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
