package com.ibm.team.rtc.extensions.workitem.copy.internal;

import java.security.SecureRandom;
import java.security.cert.X509Certificate;

import javax.naming.AuthenticationException;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientHandlerException;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.core.util.Base64;

public class JTricksRESTClient {

	private static String BASE_URL = "https://gbsjiratest.in.edst.ibm.com";

	public static void main(String[] args) {

		String auth = new String(Base64.encode("libertydevlocal:libertydevlocal"));
		// Create a trust manager that does not validate certificate chains.
		// without this when you execute you get clienthandlerexception
		TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
			public X509Certificate[] getAcceptedIssuers() {
				return null;
			}

			public void checkClientTrusted(X509Certificate[] certs, String authType) {
			}

			public void checkServerTrusted(X509Certificate[] certs, String authType) {
			}
		} };

		// Install the all-trusting trust manager
		try {
			SSLContext sc = SSLContext.getInstance("TLS");
			sc.init(null, trustAllCerts, new SecureRandom());
			HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
		} catch (Exception e) {
			;
		}

		try {
			// Get Projects
			String projects = invokeGetMethod(auth, BASE_URL + "/rest/api/2/project");
			System.out.println(projects);
			JSONArray projectArray = new JSONArray(projects);
			for (int i = 0; i < projectArray.length(); i++) {
				JSONObject proj = projectArray.getJSONObject(i);
				System.out.println("Key:" + proj.getString("key") + ", Name:" + proj.getString("name"));
			}

			// Create Issue
			String createIssueData = "{\"fields\":{\"project\":{\"key\":\"HELLHE5\"},\"summary\":\"REST Test\",\"issuetype\":{\"name\":\"Bug\"}}}";
			String issue = invokePostMethod(auth, BASE_URL + "/rest/api/2/issue", createIssueData);
			System.out.println(issue);
			// JSONObject issueObj = new JSONObject(issue);
			// String newKey = issueObj.getString("key");
			// System.out.println("Key:"+newKey);

			// Update Issue
			// String editIssueData =
			// "{\"fields\":{\"assignee\":{\"name\":\"test\"}}}";
			// invokePutMethod(auth, BASE_URL+"/rest/api/2/issue/"+newKey,
			// editIssueData);

			// invokeDeleteMethod(auth, BASE_URL+"/rest/api/2/issue/DEMO-13");

		} catch (AuthenticationException e1) {
			System.out.println("Username or Password wrong!");
			e1.printStackTrace();
		} catch (ClientHandlerException e2) {
			System.out.println("Error invoking REST method");
			e2.printStackTrace();
		} catch (JSONException e3) {
			System.out.println("Invalid JSON output");
			e3.printStackTrace();
		}

	}

	private static String invokeGetMethod(String auth, String url)
			throws AuthenticationException, ClientHandlerException {
		Client client = Client.create();
		WebResource webResource = client.resource(url);
		ClientResponse response = webResource.header("Authorization", "Basic " + auth).type("application/json")
				.accept("application/json").get(ClientResponse.class);
		int statusCode = response.getStatus();
		if (statusCode == 401) {
			throw new AuthenticationException("Invalid Username or Password");
		}
		return response.getEntity(String.class);
	}

	public static String invokePostMethod(String auth, String url, String data)
			throws AuthenticationException, ClientHandlerException {
		Client client = Client.create();
		WebResource webResource = client.resource(url);
		ClientResponse response = webResource.header("Authorization", "Basic " + auth).type("application/json")
				.accept("application/json").post(ClientResponse.class, data);
		int statusCode = response.getStatus();
		if (statusCode == 401) {
			throw new AuthenticationException("Invalid Username or Password");
		}
		return response.getEntity(String.class);
	}

	private static void invokePutMethod(String auth, String url, String data)
			throws AuthenticationException, ClientHandlerException {
		Client client = Client.create();
		WebResource webResource = client.resource(url);
		ClientResponse response = webResource.header("Authorization", "Basic " + auth).type("application/json")
				.accept("application/json").put(ClientResponse.class, data);
		int statusCode = response.getStatus();
		if (statusCode == 401) {
			throw new AuthenticationException("Invalid Username or Password");
		}
	}

	private static void invokeDeleteMethod(String auth, String url)
			throws AuthenticationException, ClientHandlerException {
		Client client = Client.create();
		WebResource webResource = client.resource(url);
		ClientResponse response = webResource.header("Authorization", "Basic " + auth).type("application/json")
				.accept("application/json").delete(ClientResponse.class);
		int statusCode = response.getStatus();
		if (statusCode == 401) {
			throw new AuthenticationException("Invalid Username or Password");
		}
	}

}