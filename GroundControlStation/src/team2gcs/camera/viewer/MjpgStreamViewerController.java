package team2gcs.camera.viewer;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.net.URL;
import java.util.Date;
import java.util.ResourceBundle;

import javax.imageio.ImageIO;

import gcs.network.Network;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.WritableImage;
import javafx.scene.input.MouseEvent;
import javafx.util.Duration;
import netscape.javascript.JSObject;
import team2gcs.camera.CameraStreamB;
import team2gcs.camera.CameraStreamF;

public class MjpgStreamViewerController implements Initializable {
    @FXML private Canvas canvas;
    @FXML private Canvas canvas2;
	@FXML private Button captureBtn;
	public JSObject jsproxy;
    
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        //httpView();
        mqttView();        
        captureBtn.setOnMouseClicked((event) -> {try {handleCaptureBtn(event);} catch (Exception e) {e.printStackTrace();}});
		
    }
    /*private void httpView() {
        try {
            CameraStream camStream = new CameraStream("http://192.168.3.41:50005/?action=stream", canvas);
            camStream.start();
        } catch(Exception e) {
            e.printStackTrace();
        }
    }*/
    
    private void mqttView() {
        try {
//        	CameraStream front = new CameraStream("tcp://192.168.3.16:1883", "/uav2/cameraFront", canvas);
//        	CameraStream bottom = new CameraStream("tcp://192.168.3.16:1883", "/uav2/cameraBottom", canvas2);
        	CameraStreamB bottom = new CameraStreamB(canvas2);
        	CameraStreamF front = new CameraStreamF(canvas);
        	
        	bottom.start();
            front.start();
            
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

	public void handleCaptureBtn (MouseEvent event) throws Exception {
		System.out.println("capture");
		Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(0.05), evt -> captureBtn.setVisible(false)),
                new KeyFrame(Duration.seconds( 0.1), evt -> captureBtn.setVisible(true)));
		timeline.setCycleCount(1);
		timeline.play();
		
		SnapshotParameters parameters = new SnapshotParameters();
        WritableImage wi = new WritableImage(320, 240);
        WritableImage snapshot = canvas.snapshot(new SnapshotParameters(), wi);

        File output = new File("camera" + new Date().getTime() + ".png");
        ImageIO.write(SwingFXUtils.fromFXImage(snapshot, null), "png", output);
        /*Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(0.05), evt -> imgdownloadLabel.setVisible(false)),
                new KeyFrame(Duration.seconds( 0.1), evt -> imgdownloadLabel.setVisible(true)));
		timeline.setCycleCount(1);
		timeline.play();

		BufferedImage img = ImageIO.read(new ByteArrayInputStream(Network.getUav().imgbyte));
		
	    File output = new File("camera" + new Date().getTime() + ".png");
	    ImageIO.write(img, "png", output);*/
	}
	
}
