package com.jerson.hcdc_portal.viewmodel;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.jerson.hcdc_portal.PortalApp;
import com.jerson.hcdc_portal.listener.DynamicListener;
import com.jerson.hcdc_portal.listener.OnHttpResponseListener;
import com.jerson.hcdc_portal.model.DashboardModel;
import com.jerson.hcdc_portal.network.HttpClient;
import com.jerson.hcdc_portal.repo.DashboardRepo;
import com.jerson.hcdc_portal.util.PreferenceManager;

import org.jsoup.nodes.Document;

import java.util.HashMap;
import java.util.List;

import okhttp3.FormBody;

public class LoginViewModel extends ViewModel {
    MutableLiveData<Integer> resCode = new MutableLiveData<>();
    MutableLiveData<List<DashboardModel>> dashboardData = new MutableLiveData<>();
    MutableLiveData<String> res = new MutableLiveData<>();
    MutableLiveData<Throwable> err = new MutableLiveData<>();
    public void Login(String email, String pass, DynamicListener<Boolean> listener) {
        getToken().observeForever(resp -> {
            if (resp != null) {
                FormBody formBody = new FormBody.Builder()
                        .add("_token", resp)
                        .add("email", email)
                        .add("password", pass)
                        .build();

                HttpClient.getInstance().POST(PortalApp.baseUrl + PortalApp.loginPostUrl, formBody, new OnHttpResponseListener<Document>() {
                    @Override
                    public void onResponse(Document response) {
                        boolean wrongPass = response.body().text().contains("CROSSIAN LOG-IN");

                        dashboardData.postValue(DashboardRepo.parseDashboard(response));

                        if (wrongPass) {
                            err.setValue(new Throwable("Incorrect Credentials!"));
                            listener.dynamicListener(false);
                        }
                        if (!wrongPass) {
                            listener.dynamicListener(true);
                            PortalApp.parseUser(response);
                        }


                    }

                    @Override
                    public void onFailure(Exception e) {
                        err.setValue(e);
                    }

                    @Override
                    public void onResponseCode(int code) {
                        resCode.setValue(code);
                    }

                });

            }
        });

    }

    public MutableLiveData<Integer> getResCode() {
        return resCode;
    }

    public MutableLiveData<String> getResponse() {
        return res;
    }

    public LiveData<List<DashboardModel>> getDashboard() {
        return dashboardData;
    }

    public MutableLiveData<Throwable> getErr() {
        return err;
    }

    MutableLiveData<String> getToken() {
        MutableLiveData<String> s = new MutableLiveData<>();
        HttpClient.getInstance().GET(PortalApp.baseUrl + PortalApp.loginUrl, new OnHttpResponseListener<Document>() {

            @Override
            public void onResponse(Document response) {
                s.setValue(response.select("input[name=_token]").first().attr("value"));
            }

            @Override
            public void onFailure(Exception e) {
                err.setValue(e);
            }

            @Override
            public void onResponseCode(int code) {
                resCode.setValue(code);
            }


        });
        return s;
    }

    public void checkSession(DynamicListener<Boolean> listener) {
        HttpClient.getInstance().GET_Redirection(PortalApp.baseUrl + PortalApp.gradesUrl, new OnHttpResponseListener<Document>() {
            @Override
            public void onResponse(Document response) {
                boolean isLoginPage = response.body().text().contains("CROSSIAN LOG-IN");
                listener.dynamicListener(isLoginPage);
            }

            @Override
            public void onFailure(Exception e) {
                err.postValue(e);
            }

            @Override
            public void onResponseCode(int code) {
                resCode.postValue(code);
            }
        });
    }

}
