import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.sql.ResultSet;

public class FinanceBot extends TelegramLongPollingBot {

    private final Map<Long, Integer> userBalance = new HashMap<>();
    private final Map<Long, Integer> tempData = new HashMap<>();
    private final Map<Long, String> userStates = new HashMap<>();

    private void showMainMenu(long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(Labels.CHOOSE_ACTION);

        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setResizeKeyboard(true);

        List<KeyboardRow> keyboard = new ArrayList<>();
        KeyboardRow row1 = new KeyboardRow();
        row1.add(Labels.ADD_INCOME);
        row1.add(Labels.ADD_EXPENSE);
        keyboard.add(row1);

        KeyboardRow row2 = new KeyboardRow();
        row2.add(Labels.REPORT);
        row2.add(Labels.BALANCE);
        row2.add(Labels.RESET_ALL_DATA);
        keyboard.add(row2);

        keyboardMarkup.setKeyboard(keyboard);
        message.setReplyMarkup(keyboardMarkup);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    // Новый метод для обработки пользовательского ввода
    private void handleInput(long chatId, String input) {
        try {
            int amount = Integer.parseInt(input);
            if (amount < 0) {
                sendMessage(chatId, Labels.ENTER_POSITIVE_NUMBER);
                return;
            }
            tempData.put(chatId, amount);

            // Определяем следующее состояние на основе текущего
            String currentState = userStates.get(chatId);
            if ("waiting_for_income_amount".equals(currentState)) {
                sendMessage(chatId, Labels.ENTER_INCOME_CATEGORY);
                userStates.put(chatId, "waiting_for_income_category");
            } else if ("waiting_for_expense_amount".equals(currentState)) {
                sendMessage(chatId, Labels.ENTER_EXPENSE_CATEGORY);
                userStates.put(chatId, "waiting_for_expense_category");
            }
        } catch (NumberFormatException e) {
            sendMessage(chatId, Labels.INPUT_ERROR_INVALID_NUMBER);
        }
    }

    // Новый метод для обработки сообщений
    private void processMessage(long chatId, String messageText) {
        if (userStates.containsKey(chatId)) {
            String state = userStates.get(chatId);

            if ("waiting_for_income_amount".equals(state) || "waiting_for_expense_amount".equals(state)) {
                handleInput(chatId, messageText);

            } else if ("waiting_for_income_category".equals(state)) {
                addIncome(chatId, tempData.get(chatId), messageText);
                sendMessage(chatId, Labels.INCOME_ADDED + messageText);
                userStates.remove(chatId);
                tempData.remove(chatId);
                showMainMenu(chatId);

            } else if ("waiting_for_expense_category".equals(state)) {
                addExpense(chatId, tempData.get(chatId), messageText);
                sendMessage(chatId, Labels.EXPENSE_ADDED + messageText);
                userStates.remove(chatId);
                tempData.remove(chatId);
                showMainMenu(chatId);

            } else if ("confirm_reset".equals(state)) {
                handleResetConfirmation(chatId, messageText);

            } else {
                sendMessage(chatId, Labels.UNKNOWN_STATE);
                userStates.remove(chatId);
                showMainMenu(chatId);
            }

        } else {
            if ("/start".equals(messageText)) {
                sendMessage(chatId, Labels.WELCOME_MESSAGE);
                showMainMenu(chatId);

            } else if (Labels.ADD_INCOME.equals(messageText)) {
                sendMessage(chatId, Labels.ENTER_INCOME_AMOUNT);
                userStates.put(chatId, "waiting_for_income_amount");

            } else if (Labels.ADD_EXPENSE.equals(messageText)) {
                sendMessage(chatId, Labels.ENTER_EXPENSE_AMOUNT);
                userStates.put(chatId, "waiting_for_expense_amount");

            } else if (Labels.BALANCE.equals(messageText)) {
                sendMessage(chatId, getBalance(chatId));
                showMainMenu(chatId);

            } else if (Labels.REPORT.equals(messageText)) {
                sendMessage(chatId, generateReport(chatId));
                showMainMenu(chatId);

            } else if (Labels.RESET_ALL_DATA.equals(messageText)) {
                sendMessage(chatId, Labels.RESET_CONFIRMATION);
                userStates.put(chatId, "confirm_reset");

            } else {
                sendMessage(chatId, Labels.UNKNOWN_COMMAND);
                showMainMenu(chatId);
            }
        }
    }


    public void saveTransaction(long userId, String type, String category, int amount) {
        String sql = "INSERT INTO transactions (user_id, type, category, amount, date) VALUES (?, ?, ?, ?, datetime('now'))";

        try (Connection conn = Database.connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, userId);
            pstmt.setString(2, type);
            pstmt.setString(3, category);
            pstmt.setInt(4, amount);

            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void confirmDataReset(long chatId) {
        sendMessage(chatId, Labels.RESET_CONFIRMATION);
        userStates.put(chatId, "confirm_reset");
    }

    private void handleResetConfirmation(long chatId, String messageText) {
        if (messageText.equalsIgnoreCase(Labels.YES)) {
            Transaction.resetUserData(chatId);
            sendMessage(chatId, Labels.DATA_DELETED);
        } else if (messageText.equalsIgnoreCase(Labels.NO)) {
            sendMessage(chatId, Labels.RESET_CANCELED);
        } else {
            sendMessage(chatId, Labels.RESET_CONFIRMATION);
            return;
        }
        // Сбрасываем состояние пользователя после ответа
        userStates.remove(chatId);
        showMainMenu(chatId);
    }

    public int loadUserBalance(long userId) {
        String sql = "SELECT COALESCE(SUM(CASE WHEN type = 'income' THEN amount WHEN type = 'expense' THEN -amount END), 0) AS balance " +
                "FROM transactions WHERE user_id = ?";
        int balance = 0;

        try (Connection conn = Database.connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, userId);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                balance = rs.getInt("balance");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return balance;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String messageText = update.getMessage().getText();
            long chatId = update.getMessage().getChatId();

            // Загружаем баланс пользователя при первом обращении
            if (!userBalance.containsKey(chatId)) {
                int balance = loadUserBalance(chatId);
                userBalance.put(chatId, balance);
            }

            processMessage(chatId, messageText);
        }
    }

    private void addIncome(long chatId, int amount, String category) {
        int newBalance = userBalance.getOrDefault(chatId, 0) + amount;
        userBalance.put(chatId, newBalance);

        // Сохраняем транзакцию в базе данных
        saveTransaction(chatId, "income", category, amount);
    }

    private void addExpense(long chatId, int amount, String category) {
        int newBalance = userBalance.getOrDefault(chatId, 0) - amount;
        userBalance.put(chatId, newBalance);

        // Сохраняем транзакцию в базе данных
        saveTransaction(chatId, "expense", category, amount);
    }

    private String getBalance(long chatId) {
        int income = getTotalIncome(chatId);
        int expense = getTotalExpense(chatId);
        int balance = income - expense;
        return Labels.CURR_BALANCE + balance + Labels.CURRENCY_NAME;
    }

    // Метод для получения общей суммы доходов пользователя
    private int getTotalIncome(long chatId) {
        String query = "SELECT SUM(amount) FROM transactions WHERE user_id = ? AND type = 'income'";
        try (Connection conn = Database.connect();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setLong(1, chatId);
            ResultSet rs = pstmt.executeQuery();
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    // Метод для получения общей суммы расходов пользователя
    private int getTotalExpense(long chatId) {
        String query = "SELECT SUM(amount) FROM transactions WHERE user_id = ? AND type = 'expense'";
        try (Connection conn = Database.connect();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setLong(1, chatId);
            ResultSet rs = pstmt.executeQuery();
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    // Метод для генерации отчета
    private String generateReport(long chatId) {
        StringBuilder report = new StringBuilder();
        int totalIncome = 0;
        int totalExpense = 0;

        report.append(Labels.INCOMES);

        // Получаем все доходы пользователя и добавляем их в отчет
        String incomeQuery = "SELECT category, amount, date FROM transactions WHERE user_id = ? AND type = 'income'";
        try (Connection conn = Database.connect();
             PreparedStatement pstmt = conn.prepareStatement(incomeQuery)) {
            pstmt.setLong(1, chatId);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                String category = rs.getString("category");
                int amount = rs.getInt("amount");
                String date = rs.getString("date");
                report.append(String.format(" - %s: %d грн (%s)\n", category, amount, date));
                totalIncome += amount; // Суммируем доходы
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        // Добавляем итоговую строку для доходов
        report.append(Labels.TOTAL_INCOMES).append(totalIncome).append(" грн\n\n");

        report.append("Расходы:\n");

        // Получаем все расходы пользователя и добавляем их в отчет
        String expenseQuery = "SELECT category, amount, date FROM transactions WHERE user_id = ? AND type = 'expense'";
        try (Connection conn = Database.connect();
             PreparedStatement pstmt = conn.prepareStatement(expenseQuery)) {
            pstmt.setLong(1, chatId);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                String category = rs.getString("category");
                int amount = rs.getInt("amount");
                String date = rs.getString("date");
                report.append(String.format(" - %s: %d грн (%s)\n", category, amount, date));
                totalExpense += amount; // Суммируем расходы
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        // Добавляем итоговую строку для расходов
        report.append("Итого расходов: ").append(totalExpense).append(" грн\n");

        return report.toString();
    }

    private void sendMessage(long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(text);
        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    @Override
    public String getBotUsername() {
        return "GetMyBudgetBot";
    }

    @Override
    public String getBotToken() {
        return "7828819760:AAHlaSoDR0kPGD7aHmVO5_gboU_ScNPSOZQ";
    }
}
