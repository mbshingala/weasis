package com.slk.fxml;

import java.awt.EventQueue;

import javax.swing.UIManager;

import org.weasis.launcher.WeasisLauncher;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

public class SLKMain extends Application {

	@Override
	public void start(Stage primaryStage) throws Exception {
		// TODO Auto-generated method stub
		Button btn = new Button();
		btn.setText("Say 'Hello World'");
		btn.setOnAction(new EventHandler<ActionEvent>() {

			@Override
			public void handle(ActionEvent event) {
				System.out.println("Hello World!");
				createSwingContent();
			}
		});

		StackPane root = new StackPane();
		root.getChildren().add(btn);

		Scene scene = new Scene(root, 300, 250);

		primaryStage.setTitle("Hello World!");
		primaryStage.setScene(scene);
		primaryStage.show();

	}

	private void createSwingContent() {
		EventQueue.invokeLater(new Runnable() {
			@Override
			public void run() {
				try {
					UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
				} catch (Exception ex) {
					ex.printStackTrace();
				}

//				final JFrame frame = new JFrame("HMIS 3D Dicom Viewer");
//				frame.setLayout(new BorderLayout());
//				
//				// get the screen size as a java dimension
//				Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
//				frame.setSize(new Dimension(screenSize.width-100, screenSize.height-100));
				
				Platform.runLater(new Runnable() {
					@Override
					public void run() {
						try {
							WeasisLauncher weasisLauncher = new WeasisLauncher();
		    				String[] args = new String[1];
		    				args[0] = "$dicom:get -l \"E:\\\\Dicom\\\\Images\"";
		    				weasisLauncher.launch(args);
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				});

//				frame.setVisible(true);
//
//				frame.setLocationRelativeTo(null);
//				frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
			}
		});
	}
	
	public static void main(String[] args) {
		launch(args);
	}

}
