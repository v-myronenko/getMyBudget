import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

}
