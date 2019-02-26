package edu.bsu.rdgunderson.armchairstormchaserapp;

import android.app.Application;

import java.net.URISyntaxException;

import io.socket.client.IO;
import io.socket.client.Socket;

public class ArmchairStormChaser extends Application {

    private Socket mSocket;

    public Socket getSocket(){
        if(mSocket == null) {
            try {
                mSocket = IO.socket(Constants.SERVER_URL);
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }
        }
        return mSocket;
    }

}
