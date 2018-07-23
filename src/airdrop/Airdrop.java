/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package airdrop;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.util.Calendar;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * @author bobby
 */
public class Airdrop {

    private static String contract;
    private static String currencySymbol;
    public static String cleosServer;
    private static int rowsAtATime;
    private static BigDecimal dropRatio;
    private static int msBetweenTx;
    private static String outputFile;

    static String sJdbc;
    static String sUser;
    static String sPass;

    public static void main(String[] args) throws SQLException, FileNotFoundException {
        Connection myConnection = null;

        try {
            getProps();

            PrintStream outFile = new PrintStream(new File(outputFile));
            PrintStream console = System.out;
            System.setOut(outFile);

            myConnection = getConnection();

            int startIndex = getStartIndex(myConnection);
            int remainingRows = getRowCount(myConnection);
            while (remainingRows > 0) {

                EosAccount[] airdropRecipients = getEosAcounts(myConnection, startIndex, rowsAtATime);

                for (EosAccount airdropRecipient : airdropRecipients) {

                    String quant = calculateDropSize(airdropRecipient);

                    logSending(airdropRecipient, quant, myConnection);

                    String transaction = sendCoins(airdropRecipient, quant);

                    if (!transaction.contentEquals("0")) {
                        logSent(airdropRecipient, quant, transaction, myConnection);
                    } else {
                        rollback(airdropRecipient, myConnection);
                    }

                    System.out.println("------------------");
                    Thread.sleep(msBetweenTx);
                }

                remainingRows = getRowCount(myConnection);
                startIndex = getStartIndex(myConnection);
            }

        } catch (Exception e) {
            System.out.println("----Exception---\n");
            System.out.println(e.getMessage());
            e.printStackTrace();
        } finally {
            if (myConnection != null) {
                myConnection.close();
            }
        }
    }

    private static String sendCoins(EosAccount recipient, String quantity) throws Exception {
        System.out.println(String.format("%s sendCoins to:%s, %s", now(), recipient.Id, quantity));
        String dropQuantity = String.format("%s %s", quantity, currencySymbol);

        String cleosCommand = String.format("cmd=push|action|%s|issue|[ \"%s\", \"%s\", \"\"]|-p|%s",
                new Object[]{contract, recipient.EosAccount, dropQuantity, contract});

        System.out.println(cleosCommand);
        String ret;
        ret = HttpUtils.HttpPostReceive(cleosServer, cleosCommand);
        System.out.println(ret);

        if (ret.contains("Error 3080002: Transaction exceeded the current network usage limit imposed on the transaction")) {
            System.out.println("Need to stake more Net: \n" + ret);
            throw new Exception("Need to stake more Net");
        } else if (ret.contains("Error 3120003: Locked wallet")) {
            System.out.println("Locked Wallet, please unlock the wallet: \n" + ret);
            throw new Exception("Locked Wallet.");
        } else if (ret.contains("Error 3120006: No available wallet")) {
            System.out.println("KEOSD didn't have any wallets open please open and unlock the wallet: \n" + ret);
            throw new Exception("No Wallets Open");
        } else if (ret.contains("Error 3080006: Transaction took too long")) {
            ret = "0";
        } else if (ret.contains("Error 3080001: account using more than allotted RAM usage")) {
            System.out.println("Need to buy more RAM: \n" + ret);
            throw new Exception("Need to buy more RAM");
        } else if (ret.contains("Error 3081001: Transaction reached the deadline set due to leeway on account CPU limits")) {
            System.out.println("Need to stake more CPU: \n" + ret);
            throw new Exception("Need to stake more CPU");
        } else if (ret.contains("Error 3080004: transaction exceeded the current CPU usage limit imposed on the transaction")) {
            System.out.println("Need to stake more CPU: \n" + ret);
            throw new Exception("Need to stake more CPU");
        } else if (ret.contains("Error") || !ret.contains("executed transaction:")) {
            System.out.println("Unexpected response from cleos: \n" + ret);
            throw new Exception("Unexpected response from cleos: \n" + ret);
        } else {
            Pattern pattern = Pattern.compile("executed transaction: ([^\\s]+)");
            Matcher matcher = pattern.matcher(ret);
            if (matcher.find()) {
                ret = matcher.group(1);
            }

        }

        return ret;
    }

    private static int getStartIndex(Connection myConnection) throws SQLException {
        String sSq = "SELECT Id "
                + "FROM AWM.EosGenisis "
                + "WHERE DroppedTo=0 ORDER BY Id asc;";
        java.sql.Statement statement = myConnection.createStatement();
        ResultSet rs = statement.executeQuery(sSq);
        if (rs != null) {
            rs.first();
            return rs.getInt("Id");
        }
        return -1;
    }

    private static int getRowCount(Connection myConnection) throws SQLException {
        String sSq = "SELECT count(*) "
                + "FROM AWM.EosGenisis "
                + "WHERE DroppedTo=0;";
        java.sql.Statement statement = myConnection.createStatement();
        ResultSet rs = statement.executeQuery(sSq);
        if (rs != null) {
            rs.first();
            return rs.getInt("count(*)");
        }
        return -1;
    }

    private static EosAccount[] getEosAcounts(Connection myConnection,
            int startIndex, int count) throws SQLException {
        EosAccount[] ret = new EosAccount[count];
        String sSql = String.format("SELECT * "
                + "FROM AWM.EosGenisis "
                + "WHERE DroppedTo=0 "
                + "ORDER BY Id asc "
                + "LIMIT %s;", count);
        java.sql.Statement statement = myConnection.createStatement();
        ResultSet rs = statement.executeQuery(sSql);

        boolean hasData = rs.next();
        int i = 0;
        while (hasData) {
            EosAccount account = new EosAccount();
            ret[i] = account;
            account.EosAccount = rs.getString("EosAccount");
            account.EosAddress = rs.getString("EosAddress");
            account.EthAddress = rs.getString("EthAddress");
            account.Id = rs.getString("Id");
            account.Quantity = rs.getString("Quantity");

            hasData = rs.next();
            i++;
        }

        return ret;
    }

    private static void logSent(EosAccount airdropRecipient, String sQuantity,
            String sBlock, Connection conn) throws SQLException {

        String sql = String.format("INSERT INTO `AWM`.`AirDrop` (`EosGenisisID`,"
                + " `Quantity`, `EosTransaction`) VALUES (\"%s\", \"%s\", \"%s\");",
                airdropRecipient.Id, sQuantity, sBlock);
        System.out.println(now() + " sql:" + sql);
        sqlWrite(conn, sql);

        sql = String.format("UPDATE `AWM`.`EosGenisis` SET LastDrop=now(), DroppedTo=1"
                + " WHERE id=%s ;", airdropRecipient.Id);
        System.out.println(now() + " sql:" + sql);
        sqlWrite(conn, sql);

    }

    static void sqlWrite(Connection conn, String sSql) throws SQLException {

        java.sql.PreparedStatement stmt = conn.prepareStatement(sSql);
        stmt.execute();
    }

    private static String calculateDropSize(EosAccount recipient) {
        BigDecimal dQuant = new BigDecimal(recipient.Quantity);
        dQuant = dQuant.multiply(dropRatio);

        DecimalFormat df = new DecimalFormat();
        df.setGroupingUsed(false);
        df.setMaximumFractionDigits(4);
        df.setMinimumFractionDigits(4);

        return df.format(dQuant);
    }

    private static void logSending(EosAccount airdropRecipient, String quant,
            Connection conn) throws SQLException {
        System.out.println(String.format("%s logSending %s, %s", now(),
                airdropRecipient.Id, quant));

        String sql = String.format("update `AWM`.EosGenisis set LastDrop=now(), "
                + "DroppedTo=-1 where id=%s ;", airdropRecipient.Id);
        sqlWrite(conn, sql);

    }

    static String now() {
        return Calendar.getInstance().getTime().toString();
    }

    private static void rollback(EosAccount airdropRecipient, Connection myConnection)
            throws SQLException {
        System.out.println("Putting drop account back to undropped ");
        String sql = String.format("update `AWM`.`EosGenisis` SET LastDrop=null, DroppedTo=0 WHERE id=%s ;", airdropRecipient.Id);
        sqlWrite(myConnection, sql);
    }

    private static void getProps() throws FileNotFoundException, IOException {
        Properties prop = new Properties();
        InputStream input = new FileInputStream("config.properties");
        prop.load(input);

        // get the property value and print it out
        contract = prop.getProperty("contract");
        currencySymbol = prop.getProperty("currencySymbol");
        cleosServer = prop.getProperty("cleosServer");
        rowsAtATime = Integer.parseInt(prop.getProperty("rowsAtATime"));
        dropRatio = new BigDecimal(prop.getProperty("dropRatio"));
        msBetweenTx = Integer.parseInt(prop.getProperty("msBetweenTx"));
        outputFile = prop.getProperty("outputFile");

        sJdbc = prop.getProperty("sJdbc");
        sUser = prop.getProperty("sUser");
        sPass = prop.getProperty("sPass");
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(sJdbc, sUser, sPass);
    }
}
