/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package airdrop;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author bobby
 */
public class HttpUtils {

    public static String doHttpRequest(String sAddr, String sData, String sMethod,
            Proxy proxy, HashMap<String, String> connectionRequestProps) {

        String sRet = "";
        try {
            HttpURLConnection connection;
            URL u = new URL(sAddr);
            if (proxy == null) {
                connection = (HttpURLConnection) u.openConnection();
            } else {
                connection = (HttpURLConnection) u.openConnection(proxy);
            }
            connection.setDoOutput(true);
            connection.setDoInput(true);
            connection.setInstanceFollowRedirects(false);
            sMethod = sMethod != null && sMethod.equals("GET") ? sMethod : "POST";
            connection.setRequestMethod(sMethod);
            connection.setUseCaches(false);
            connection.setConnectTimeout(30000);
            connection.setReadTimeout(30000);
            if (connectionRequestProps == null) {
                connectionRequestProps = new HashMap<String, String>();
                connectionRequestProps.put("Content-Type", "application/x-www-form-urlencoded");
                connectionRequestProps.put("charset", "utf-8");
                connectionRequestProps.put("Content-Length", "" + Integer.toString(sData.getBytes().length));
            }
            for (Map.Entry<String, String> entrySet : connectionRequestProps.entrySet()) {
                String key = entrySet.getKey();
                String value = entrySet.getValue();
                connection.setRequestProperty(key, value);
            }
            DataOutputStream wr = new DataOutputStream(connection.getOutputStream());
            wr.writeBytes(sData);
            wr.flush();
            BufferedReader rd = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String sLine = "";
            while ((sLine = rd.readLine()) != null) {
                sRet += sLine + "\n";
            }
            wr.close();
            rd.close();
            connection.disconnect();
        } catch (Exception e) {
            System.out.println("HttpPostReceive Error: " + e.getLocalizedMessage());
        }
        return sRet;
    }

    public static String HttpPostReceive(String sAddr, String sData) {
        try {
            return doHttpRequest(sAddr, sData, "POST", null, null);
        } catch (Exception e) {
            System.out.println("HttpPostReceive Error: " + e.getLocalizedMessage());
        }
        return "";
    }

}
