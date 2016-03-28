/*
 * Copyright 2015 CptWin.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nz.co.lolnet.statistics;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.json.simple.JSONObject;

/**
 *
 * @author CptWin
 */
public class LauncherStatistics {

    public static void launcherIsLaunched() throws IOException {
        try {
            String data = "input={\"ipaddress\":\"" + getIp() + "\"}";
            // Send data
            URL url = new URL("https://www.lolnet.co.nz/api/v1.0/lolstats/launcher/launched.php");
            URLConnection conn = url.openConnection();
            conn.setDoOutput(true);
            conn.setConnectTimeout(30000);
            // Get the response
            OutputStreamWriter wr = new OutputStreamWriter(conn.getOutputStream());
            wr.write(data);
            wr.flush();

            BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String s = rd.readLine();
            wr.close();
            rd.close();
        } catch (IOException ex) {
            //Logger.getLogger(ThreadLauncherIsLaunched.class.getName()).log(Level.SEVERE, null, ex);
            //Just die quietly, something went wrong but its not the end of the world.
        }
    }

    public static void installModPack(String title, String version) throws IOException {
        try {
            String data = "input={\"title\":\"" + title + "\",\"version\":\"" + version + "\"}";
            // Send data
            URL url = new URL("https://www.lolnet.co.nz/api/v1.0/lolstats/launcher/installed_modpack.php");
            URLConnection conn = url.openConnection();
            conn.setDoOutput(true);
            conn.setConnectTimeout(30000);
            // Get the response
            OutputStreamWriter wr = new OutputStreamWriter(conn.getOutputStream());
            wr.write(data);
            wr.flush();

            BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String s = rd.readLine();
            wr.close();
            rd.close();
        } catch (IOException ex) {
            //Logger.getLogger(ThreadLauncherIsLaunched.class.getName()).log(Level.SEVERE, null, ex);
            //Just die quietly, something went wrong but its not the end of the world.
        }
    }

    public static boolean sendMetrics(JSONObject meta) throws IOException {
        boolean result = false;
        try {
            // Construct data

            String data = URLEncoder.encode("meta", "UTF-8") + "=" + URLEncoder.encode(meta.toJSONString(), "UTF-8");
            // Send data
            URL url = new URL("https://www.lolnet.co.nz/api/v1.0/lolstats/launcher/sendMetrics.php");
            URLConnection conn = url.openConnection();
            conn.setDoOutput(true);
            OutputStreamWriter wr = new OutputStreamWriter(conn.getOutputStream());
            wr.write(data);
            wr.flush();

            // Get the response
            BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String line = null;
            while ((line = rd.readLine()) != null) {
                if (line.toLowerCase().contains("true")) {
                    result = true;
                    break;
                }
            }
            wr.close();
            rd.close();
        } catch (Exception e) {
            return result;
        }
        return result;
    }

    public static void sendFeedback(String message, String meta) throws IOException {
        try {
            String data = "input={\"message\":\"" + message + "\",\"meta\":\"" + meta + "~~" + getIp() + "\"}";
            // Send data
            URL url = new URL("https://www.lolnet.co.nz/api/v1.0/lolstats/launcher/sendFeedback.php");
            URLConnection conn = url.openConnection();
            conn.setDoOutput(true);
            conn.setConnectTimeout(30000);
            // Get the response
            OutputStreamWriter wr = new OutputStreamWriter(conn.getOutputStream());
            wr.write(data);
            wr.flush();

            BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String s = rd.readLine();
            wr.close();
            rd.close();
        } catch (IOException ex) {
            Logger.getLogger(ThreadLauncherIsLaunched.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public static void launchModPack(String title) throws IOException {
        try {
            String data = "input={\"title\":\"" + title + "\"}";
            // Send data
            URL url = new URL("https://www.lolnet.co.nz/api/v1.0/lolstats/launcher/launched_modpack.php");
            URLConnection conn = url.openConnection();
            conn.setDoOutput(true);
            conn.setConnectTimeout(30000);
            // Get the response
            OutputStreamWriter wr = new OutputStreamWriter(conn.getOutputStream());
            wr.write(data);
            wr.flush();

            BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String s = rd.readLine();
            wr.close();
            rd.close();
        } catch (IOException ex) {
            //Logger.getLogger(ThreadLauncherIsLaunched.class.getName()).log(Level.SEVERE, null, ex);
            //Just die quietly, something went wrong but its not the end of the world.
        }
    }

    public static String getIp() throws IOException {
        URL whatismyip = new URL("http://checkip.amazonaws.com");
        BufferedReader in = null;
        try {
            in = new BufferedReader(new InputStreamReader(
                    whatismyip.openStream()));
            String ip = in.readLine();
            return ip;
        } finally {
            if (in != null) {
                in.close();
            }
        }
    }
}
