package com.example.weather;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.weather.db.City;
import com.example.weather.db.County;
import com.example.weather.db.Province;
import com.example.weather.gson.Weather;
import com.example.weather.util.HttpUtil;
import com.example.weather.util.Utility;

import org.jetbrains.annotations.NotNull;
import org.litepal.LitePal;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class ChooseAreaFragment extends Fragment {

    //定义三个常量表示选择的等级
    public final static int LEVEL_PROVINCE = 1;
    public final static int LEVEL_CITY = 2;
    public final static int LEVEL_COUNTRY = 3;

    //定义需要使用的控件
    private TextView title_view;
    private Button back_btn;
    private ListView listView;

    private ProgressDialog dialog;  //进度条
    private ArrayAdapter<String> adapter;  //ListView所需的适配器
    private List<String> list = new ArrayList<>();  //为ListView提供数据

    private List<Province> provinceList;
    private List<City> cityList;
    private List<County> countryList;

    //选中的省和城市
    private City selectedCity;
    private Province selectedProvince;

    //当前等级
    private int currentLevel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.choose_area , container , false);
        title_view = (TextView) view.findViewById(R.id.title_text);
        back_btn = (Button) view.findViewById(R.id.back_button);
        listView = (ListView) view.findViewById(R.id.list_view);
        adapter = new ArrayAdapter<>(getContext(),android.R.layout.simple_list_item_1,list);
        listView.setAdapter(adapter);
        return view;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if(currentLevel == LEVEL_PROVINCE)
                {
                    selectedProvince = provinceList.get(position);
                    queryCities();
                }else if(currentLevel == LEVEL_CITY)
                {
                    selectedCity = cityList.get(position);
                    queryCountries();
                }else if(currentLevel == LEVEL_COUNTRY)
                {
                    String weatherId = countryList.get(position).getWeatherId();
                    if(getActivity() instanceof MainActivity)
                    {
                        Intent intent = new Intent(getActivity(),WeatherActivity.class);
                        intent.putExtra("weather_id",weatherId);
                        startActivity(intent);
                        getActivity().finish();
                    }else if(getActivity() instanceof WeatherActivity)
                    {
                        WeatherActivity activity = (WeatherActivity) getActivity();
                        activity.mWeatherId = weatherId;
                        activity.drawerLayout.closeDrawers();
                        activity.swipeRefreshLayout.setRefreshing(true);
                        activity.requestWeather(weatherId);
                    }

                }
            }

        });

        back_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(currentLevel == LEVEL_CITY)
                {
                    queryProvinces();
                }else if(currentLevel == LEVEL_COUNTRY)
                {
                    queryCities();
                }
            }
        });
        queryProvinces();
    }

    private void queryProvinces()
    {
        title_view.setText("中国");
        back_btn.setVisibility(View.GONE);
        provinceList = LitePal.findAll(Province.class);

        if(provinceList.size() > 0)
        {
            list.clear();
            for (Province province : provinceList) {
                list.add(province.getProvinceName());
            }
            adapter.notifyDataSetChanged();
            listView.setSelection(0);
            currentLevel = LEVEL_PROVINCE;
        }else{
            String address = "http://guolin.tech/api/china";
            queryFromServer(address,"province");
        }
    }

    private void queryCities()
    {
        title_view.setText(selectedProvince.getProvinceName());
        back_btn.setVisibility(View.VISIBLE);
        cityList = LitePal.where("provinceid = ?",String.valueOf(selectedProvince.getId()))
                .find(City.class);
        if(cityList.size() > 0)
        {
            list.clear();
            for (City city : cityList) {
                list.add(city.getCityName());
            }
            adapter.notifyDataSetChanged();
            listView.setSelection(0);
            currentLevel = LEVEL_CITY;
        }else{
            String address = "http://guolin.tech/api/china/"+selectedProvince.getProvinceCode();
            queryFromServer(address,"city");
        }


    }

    private void queryCountries()
    {
        title_view.setText(selectedCity.getCityName());
        countryList = LitePal.where("cityid = ?",String.valueOf(selectedCity.getId()))
                .find(County.class);
        if(countryList.size() > 0)
        {
            list.clear();
            for (County country : countryList) {
                list.add(country.getCountyName());
            }
            adapter.notifyDataSetChanged();
            listView.setSelection(0);
            currentLevel = LEVEL_COUNTRY;
        }else{
            String address = "http://guolin.tech/api/china/"
                    +selectedProvince.getProvinceCode()+"/"
                    +selectedCity.getCityCode();
            queryFromServer(address,"country");
        }
    }

    private void queryFromServer(String address, final String type)
    {
        showProgressDialog();
        HttpUtil.sendOkHttpRequest(address, new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        closeDiaLog();
                        Toast.makeText(getContext(), "加载失败", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                String responseText = response.body().string();
                boolean result = false;
                if("province".equals(type))
                {
                    result = Utility.handleProvinceResponse(responseText);
                }else if("city".equals(type)){
                    result = Utility.handleCityResponse(responseText,selectedProvince.getId());
                }else if("country".equals(type)){
                    result = Utility.handleCountryResponse(responseText,selectedCity.getId());
                }
                if(result){
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            closeDiaLog();
                            if("province".equals(type))
                            {
                                queryProvinces();
                            }else if("city".equals(type)){
                                queryCities();
                            }else if("country".equals(type)){
                                queryCountries();
                            }
                        }
                    });

                }
            }
        });
    }

    private void closeDiaLog() {

        if(dialog != null)
        {
            dialog.dismiss();
        }

    }

    private void showProgressDialog() {
        if(dialog == null)
        {
            dialog = new ProgressDialog(getActivity());
            dialog.setMessage("正在加载。。。");
            dialog.setCanceledOnTouchOutside(false);
        }
        dialog.show();
    }

}