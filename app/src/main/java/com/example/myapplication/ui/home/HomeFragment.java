package com.example.myapplication.ui.home;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import com.example.myapplication.R;
import com.example.myapplication.TalkItemView;
import com.example.myapplication.TalkItem;
import com.example.myapplication.changeCode;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.json.JSONArray;
import org.json.JSONException;
import ai.api.AIListener;
import ai.api.AIServiceException;
import ai.api.android.AIConfiguration;
import ai.api.android.AIService;
import ai.api.android.AIDataService;
import ai.api.model.AIError;
import ai.api.model.AIRequest;
import ai.api.model.AIResponse;
import ai.api.model.Result;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import java.io.IOException;
import java.util.ArrayList;
import static android.content.Context.MODE_PRIVATE;
import static android.os.Looper.getMainLooper;

public class HomeFragment extends Fragment implements AIListener{

    private AIRequest aiRequest;
    private AIDataService aiDataService;
    private EditText chatText;
    private String TAG = "HOME_FRAGMENT", token = "YOUR_ACCESS_TOKEN", uid;
    private SharedPreferences pref, sp;
    private SharedPreferences.Editor editor;
    private ListView listview;
    private TalkAdapter talkAdapter;

    @Override
    public void onResult(AIResponse response) {
        Log.d("finished..",response.toString());
        Result result= response.getResult();
    }

    @Override
    public void onError(AIError error) { }

    @Override
    public void onAudioLevel(float level) { }

    @Override
    public void onListeningStarted() { }

    @Override
    public void onListeningCanceled() { }

    @Override
    public void onListeningFinished() { }

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {

        getActivity().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        //DialogFlow 연결
        final AIConfiguration config = new AIConfiguration(token,
                AIConfiguration.SupportedLanguages.Korean,
                AIConfiguration.RecognitionEngine.System);
        aiDataService = new AIDataService(getContext(),config);
        AIService aiService = AIService.getService(getContext(), config);
        aiRequest = new AIRequest();
        aiService.setListener(this);

        View root = inflater.inflate(R.layout.fragment_home, container, false);
        chatText = root.findViewById(R.id.chatText);
        Button sendButton = root.findViewById(R.id.sendButton);

        // 리스트 뷰 톡이랑 연결
        listview = root.findViewById(R.id.talk_list);
        talkAdapter = new TalkAdapter();
        pref=getContext().getSharedPreferences("pref", MODE_PRIVATE);
        uid = pref.getString("uid","");

        sp=getContext().getSharedPreferences("Skey",MODE_PRIVATE);
        editor = sp.edit();
        editor.putString("uid","");
        editor.apply();

        // 대화 전송 버튼 클릭 시
        sendButton.setOnClickListener(v -> {
            if(chatText.getText()!=null&& chatText.getText().length()!=0){
                String message = chatText.getText().toString().trim();
                if(!message.equals("")){
                    talkAdapter.addItem(new TalkItem(false,message));
                    chatText.setText("");
                    sendRequest(message);
                    listview.setAdapter(talkAdapter);
                    listview.setSelection(talkAdapter.getCount() - 1);
                }
            }
        });
        return root;
    }

    @SuppressLint("StaticFieldLeak")
    private void sendRequest(String message){
        aiRequest.setQuery(message);
        new AsyncTask<AIRequest, Void, AIResponse>() {
            @Override
            protected AIResponse doInBackground(AIRequest... aiRequests) {
                final AIRequest request = aiRequests[0];
                try {
                    return aiDataService.request(request);
                } catch (AIServiceException e) {
                    AIError aiError = new AIError(e);
                    onError(aiError);
                }
                return null;
            }

            @Override
            protected void onPostExecute(AIResponse aiResponse) {
                if (aiResponse != null){
                    Result result = aiResponse.getResult();
                    String response = result.getFulfillment().getSpeech();
                    String intent = result.getMetadata().getIntentName();
                    Log.d(TAG,"Intent : "+intent);
                    if(!response.equals("") && !response.equals(" ")){
                        if(response.substring(0,1).equals("!")){
                            talkAdapter.addItem(new TalkItem(true,response));
                            listview.setAdapter(talkAdapter);
                            listview.setSelection(talkAdapter.getCount() - 1);
                        }else{
                            if(response.equals("추천")){ // 사용자가 추천 복지 서비스 요청한 경우
                                // 저장된 Category 정보 가져오기
                                SharedPreferences sp=getContext().getSharedPreferences("Skey", MODE_PRIVATE);
                                String category = sp.getString("first_skey","");
                                sendRec(category);
                            }else {
                                // 복지 API 호출
                                getApiService task = new getApiService();
                                task.execute(response);
                            }
                        }
                    }
                }else{
                    Log.d(TAG,"airequest is null");
                }
            }
        }.execute(aiRequest);
    }

    //내용 기반.협업 필터링 설정
    private void sendRec(String category) {
        ArrayList<String> servlist;
        servlist = getStringArrayPref(getContext(), "u_servi");
        if (servlist.size() >= 4) { //협업 필터링 사용
            //서버로  shared 업데이트
            getReclist task = new getReclist();
            task.execute(uid);
        } else {
            //내용 기반 필터링 사용
            if(servlist.size() != 0){
                String[] str = servlist.get(servlist.size()-1).split("&&&");
                if(str[2]!=null&& !str[2].equals("")){
                    getContentBasedServices task = new getContentBasedServices();
                    task.execute(str[2]);
                    listview.setAdapter(talkAdapter);
                }
            }else{ // 사용자의 채팅 내역이 없는 경우
                getContentBasedServices task2 = new getContentBasedServices();
                task2.execute(category);
                listview.setAdapter(talkAdapter);
            }
        }
    }


    //협업필터링 추천서비스들 리턴
    @SuppressLint("StaticFieldLeak")
    public class getReclist extends AsyncTask<String,Void,String>{
        OkHttpClient client = new OkHttpClient();
        String[][] three_svlist;

        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);
        }

        @Override
        protected String doInBackground(String... params) {
            String url = "http://52.79.72.52:5000/getrecommend";
            String userid = params[0];
            HttpUrl.Builder urlBuilder = HttpUrl.parse(url).newBuilder();
            urlBuilder.addEncodedQueryParameter("uid", userid);
            String requestUrl = urlBuilder.build().toString();
            Request request = new Request.Builder()
                    .url(requestUrl)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {

                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    ResponseBody responseBodyCopy = response.peekBody(Long.MAX_VALUE);
                    String data = responseBodyCopy.string();
                    Log.d("협업 결과",data);
                    JsonParser jsonParser = new JsonParser();
                    JsonObject jsonObject = (JsonObject) jsonParser.parse(data);
                    jsonObject.get("result").toString();
                    String result = jsonObject.get("result").toString();
                    if(!result.equals("") && !result.equals("[]")){
                        result = result.substring(2,result.length()-2);
                        String[] result_list = result.split("\",\"");
                        if(result_list.length > 0){
                            three_svlist = new String[3][2];
                            for(int i=0;i<3;i++){
                                if(result_list.length>i){
                                    three_svlist[i][0]=result_list[i];
                                    three_svlist[i][1]="";

                                }else{ // result_list의 개수가 3개 보다 적을 경우
                                    break;
                                }
                            }
                            talkAdapter.addItem(new TalkItem(true,"배열",three_svlist));
                            SharedPreferences sp=getContext().getSharedPreferences("Skey", MODE_PRIVATE);
                            SharedPreferences.Editor editor=sp.edit();
                            editor.remove("text");
                            editor.putString("text","collaborative_filtering");
                            editor.apply();
                        }
                        new Handler(getMainLooper()).post(() -> {
                            listview.setAdapter(talkAdapter);
                            listview.setSelection(talkAdapter.getCount() - 1);
                        });
                    }else {
                        Log.d("협업 결과","리턴 값 null");
                    }
                }
            });
            return null;
        }
    }

    // 복지로 API에서 응답받는 경우
    @SuppressLint("StaticFieldLeak")
    public class getApiService extends AsyncTask<String,Void,String[][]>{
        String category;
        @Override
        protected void onPostExecute(String[][] ss) {
            if(ss!=null){
                talkAdapter.addItem(new TalkItem(true,"배열",ss));
                listview.setAdapter(talkAdapter);
                listview.setSelection(talkAdapter.getCount() - 1);
                SharedPreferences sp=getContext().getSharedPreferences("Skey", MODE_PRIVATE);
                SharedPreferences.Editor editor=sp.edit();

                editor.remove("text");
                editor.putString("text",category);
                editor.apply();
            }else{
                talkAdapter.addItem(new TalkItem(true,"API 오류"));
                listview.setAdapter(talkAdapter);
                listview.setSelection(talkAdapter.getCount() - 1);
            }
        }
        @Override
        protected String[][] doInBackground(String... params) {
            category = params[0];
            changeCode apiapi=new changeCode();
            return apiapi.callApi(params[0]);
        }
    }

    // 내용 기반 필터링
    @SuppressLint("StaticFieldLeak")
    public class getContentBasedServices extends AsyncTask<String,Void,String[][]> {
        OkHttpClient client = new OkHttpClient();
        String[][] three_svlist;

        @Override
        protected void onPostExecute(String[][] s) {
            if(s!=null&&s.length!=0){
                listview.setAdapter(talkAdapter);
                listview.setSelection(talkAdapter.getCount() - 1);
            }else{
                listview.setAdapter(talkAdapter);
                listview.setSelection(talkAdapter.getCount() - 1);
            }
        }

        @Override
            protected String[][] doInBackground(String... params) {
                String url = "http://52.79.72.52:5000/findinq";
                HttpUrl.Builder urlBuilder = HttpUrl.parse(url).newBuilder();
                Log.d("Category_Data","url");
                urlBuilder.addEncodedQueryParameter("catemid", params[0]);
                SharedPreferences sp=getContext().getSharedPreferences("Skey", MODE_PRIVATE);
                SharedPreferences.Editor editor=sp.edit();
                editor.remove("skey_content");
                editor.putString("skey_content",params[0]);
                editor.apply();

                String requestUrl = urlBuilder.build().toString();
                Request request = new Request.Builder()
                        .url(requestUrl)
                        .build();
                client.newCall(request).enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        Log.d("Category_Data","urldatafail");
                    }
                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        ResponseBody responseBodyCopy = response.peekBody(Long.MAX_VALUE);
                        String data = responseBodyCopy.string();
                        data = data.replace("\"(","\"{");
                        data = data.replace(")\"","}\"");

                        JsonParser Parser = new JsonParser();
                        JsonObject jsonObj = (JsonObject) Parser.parse(data);
                        JsonArray svArray = (JsonArray) jsonObj.get("result");

                        three_svlist = new String[3][2];
                        for(int i=0;i<3;i++){
                            String str = svArray.get(i).toString();
                            String[] sv_content = str.split(", '");
                            String result1 = sv_content[3].substring(0, sv_content[3].length()-1);
                            String result2 = sv_content[4].substring(0, sv_content[4].length()-1);
                            String[] a = {result1,result2};
                            three_svlist[i] = a;
                        }
                        talkAdapter.addItem(new TalkItem(true,"배열",three_svlist));
                        SharedPreferences sp=getContext().getSharedPreferences("Skey", MODE_PRIVATE);
                        SharedPreferences.Editor editor=sp.edit();
                        editor.remove("text");
                        editor.putString("text","content");
                        editor.apply();
                    }
                });
                return three_svlist;
        }
    }

    // SharedPreferences에서 "서비스명"과 "평점" 불러오기
    // 서비스명 + &&& + 평점 : u_servi
    private ArrayList<String> getStringArrayPref(Context context, String key) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String json = prefs.getString(key, null);
        ArrayList<String> urls = new ArrayList<String>();
        if (json != null) {
            try {
                JSONArray a = new JSONArray(json);
                for (int i = 0; i < a.length(); i++) {
                    String url = a.optString(i);
                    urls.add(url);
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }else{
            Log.d("getStringArrayPref","값 없음");
        }
        return urls;
    }

    //리스트 뷰 어댑터 클래스
    class TalkAdapter extends BaseAdapter {
        ArrayList<TalkItem> items = new ArrayList<>();
        @Override
        public int getCount() {
            return items.size();
        }
        @Override
        public Object getItem(int i) {
            return items.get(i);
        }
        @Override
        public long getItemId(int i) {
            return i;
        }
        public void addItem(TalkItem item){
            items.add(item);
        }
        @Override
        public View getView(int i, View v, ViewGroup viewGroup) {
            TalkItemView view = new TalkItemView(getContext());
            TalkItem item = items.get(i);

            if(!item.isBot()){ // 내가 챗봇에 입력할 때
                view.setBackGroundWhite();
                view.setTalk(item.getTalk());
            }else{
                if(!item.getTalk().equals("") && !item.getTalk().equals(" ") &&item.getTalk()!=null){
                    if(item.getTalk().equals("배열")&&item.getItems()!=null&&item.getItems().length!=0){
                        view.setBokjiList(item.getItems());
                    }else{
                        view.setTalk(item.getTalk());
                        view.setListVisible(false);
                    }
                }
            }
            return view;
        }
    }
}