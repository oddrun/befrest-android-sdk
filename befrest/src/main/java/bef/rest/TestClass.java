package bef.rest;

import org.json.JSONObject;

import java.io.Serializable;
import java.util.ArrayList;

public class TestClass implements Serializable {

    int i = 0;
    int j = 2;
    ArrayList<String> d = new ArrayList<>();
    JSONObject jsonObject = new JSONObject();

    public int getI() {
        return i;
    }
}
