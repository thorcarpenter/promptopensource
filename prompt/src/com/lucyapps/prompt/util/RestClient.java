/* Copyright 2010 NorseVision LLC, all rights reserved. */
package com.lucyapps.prompt.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URLEncoder;
import java.util.ArrayList;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;

/**
 * Borrowed from: http://lukencode.com/2010/04/27/calling-web-services-in-android-using-httpclient/
 *
 * Example usage:
 *
 * RestClient client = new RestClient(LOGIN_URL);
 * client.AddParam("accountType", "GOOGLE");
 * client.AddParam("source", "tboda-widgalytics-0.1");
 * client.AddParam("Email", _username);
 * client.AddParam("Passwd", _password);
 * client.AddParam("service", "analytics");
 * client.AddHeader("GData-Version", "2");
 * 
 * try {
 *     client.Execute(RequestMethod.POST);
 * } catch (Exception e) {
 *     e.printStackTrace();
 * }
 *
 * String response = client.getResponse();
 *
 */
public class RestClient {

    public enum RequestMethod {

        GET,
        POST
    }
    private ArrayList<NameValuePair> params;
    private ArrayList<NameValuePair> headers;
    private String url;
    private int responseCode;
    private String message;
    private String response;

    public String getResponse() {
        return response;
    }

    public String getErrorMessage() {
        return message;
    }

    public int getResponseCode() {
        return responseCode;
    }

    public RestClient(String url) {
        this.url = url;
        params = new ArrayList<NameValuePair>();
        headers = new ArrayList<NameValuePair>();
    }

    public void AddParam(String name, String value) {
        params.add(new BasicNameValuePair(name, value));
    }

    public void AddHeader(String name, String value) {
        headers.add(new BasicNameValuePair(name, value));
    }

    public void Execute(RequestMethod method) throws Exception {
        final String ENCODING = "UTF-8";
        switch (method) {
            case GET: {
                //add parameters
                StringBuilder fullURL = new StringBuilder(url);
                if (!params.isEmpty()) {
                    boolean isFirst = true;
                    for (NameValuePair p : params) {
                        if (isFirst) {
                            fullURL.append('?');
                            isFirst = false;
                        } else {
                            fullURL.append('&');
                        }
                        fullURL.append(URLEncoder.encode(p.getName(), ENCODING));
                        fullURL.append('=');
                        fullURL.append(URLEncoder.encode(p.getValue(), ENCODING));
                    }
                }

                HttpGet request = new HttpGet(fullURL.toString());

                //add headers
                for (NameValuePair h : headers) {
                    request.addHeader(h.getName(), h.getValue());
                }

                executeRequest(request, url);
                break;
            }
            case POST: {
                HttpPost request = new HttpPost(url);

                //add headers
                for (NameValuePair h : headers) {
                    request.addHeader(h.getName(), h.getValue());
                }

                if (!params.isEmpty()) {
                    request.setEntity(new UrlEncodedFormEntity(params, HTTP.UTF_8));
                }

                executeRequest(request, url);
                break;
            }
        }
    }

    private void executeRequest(HttpUriRequest request, String url) {
        HttpClient client = new DefaultHttpClient();

        HttpResponse httpResponse;

        try {
            httpResponse = client.execute(request);
            responseCode = httpResponse.getStatusLine().getStatusCode();
            message = httpResponse.getStatusLine().getReasonPhrase();

            HttpEntity entity = httpResponse.getEntity();

            if (entity != null) {

                InputStream instream = entity.getContent();
                response = convertStreamToString(instream);

                // Closing the input stream will trigger connection release
                instream.close();
            }

        } catch (ClientProtocolException e) {
            client.getConnectionManager().shutdown();
            e.printStackTrace();
        } catch (IOException e) {
            client.getConnectionManager().shutdown();
            e.printStackTrace();
        }
    }

    private static String convertStreamToString(InputStream is) {

        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();

        String line = null;
        try {
            while ((line = reader.readLine()) != null) {
                sb.append(line);
                sb.append("\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                is.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return sb.toString();
    }
}
