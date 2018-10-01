import java.io.*;
import java.net.*;
import java.util.Date;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Ellipse;
import javafx.scene.shape.Line;
import javafx.stage.Stage;

public class TicTacToeClient extends Application 
    implements TicTacToeConstants {
  // Indicate whether the player has the turn
  private boolean myTurn = false;

  // Indicate the token for the player
  private char myToken = ' ';

  // Indicate the token for the other player
  private char otherToken = ' ';

  // Create and initialize cells
  private Cell[][] cell =  new Cell[3][3];

  // Create and initialize a title label (brugt i start metoden til UI)
  private Label lblTitle = new Label();

  // Create and initialize a status label (brugt i start metoden til UI)
  private Label lblStatus = new Label();

  // Indicate selected row and column by the current move(brugt i metoder til at holde styr på onclick events)
  private int rowSelected;
  private int columnSelected;

  // Input and output streams from/to server (bare input og output streams)
  private DataInputStream fromServer;
  private DataOutputStream toServer;

  // Continue to play (for kontrol af om spillet slutter, dette bliver checket efter vært tref af en individuel client)
  private boolean continueToPlay = true;

  // Wait for the player to mark a cell (default er at du venter intil der bliver givet besked fra serveren at du ikke skal vente længere, dette sker første gang når en anden spiller opretter forbindelse efter dig)
  private boolean waiting = true;

  // Host name or ip (skal ændres manuelt)
  private String host = "localhost";


  //  lasse  \\
  //vis sætter lidt extra til start metoden for vores clienter
  //hovedsagligt gør vi bare at når appen starter åbner der et vindue
  @Override
  public void start(Stage primaryStage) {
    // Pane to hold cell
    GridPane pane = new GridPane(); 
    for (int i = 0; i < 3; i++)
      for (int j = 0; j < 3; j++)
        pane.add(cell[i][j] = new Cell(i, j), j, i);

    BorderPane borderPane = new BorderPane();
    borderPane.setTop(lblTitle);
    borderPane.setCenter(pane);
    borderPane.setBottom(lblStatus);

    
    // Create a scene and place it in the stage
    Scene scene = new Scene(borderPane, 320, 350);
    primaryStage.setTitle("TicTacToeClient"); // Set the stage title
    primaryStage.setScene(scene); // Place the scene in the stage
    primaryStage.show(); // Display the stage

    // Connect to the server
    connectToServer();
  }

  //  lasse  \\
  //en client opretter forbindelse til serveren (eller prøver)
  //efter det er sket sucessfyldt bliver clienten gjordt klar til at man kan spille spillet
  private void connectToServer() {
    try {
      // Create a socket to connect to the server
      Socket socket = new Socket(host, 8000);

      // Create an input stream to receive data from the server
      fromServer = new DataInputStream(socket.getInputStream());

      // Create an output stream to send data to the server
      toServer = new DataOutputStream(socket.getOutputStream());
    }
    catch (Exception ex) {
      ex.printStackTrace();
    }

    //  lasse  \\
    //det meste af hovet logikken bliver brugt her når two spillere skal spille sammen
    // det er også her at en client bliver sat til endten at være player et eller player to
    new Thread(() -> {
      try {
        // Get notification from the server
        int player = fromServer.readInt();
  
        // Am I player 1 or 2?
        if (player == PLAYER1) {
          myToken = 'X';
          otherToken = 'O';
          Platform.runLater(() -> {
            lblTitle.setText("Player 1 with token 'X'");
            lblStatus.setText("Waiting for player 2 to join");
          });
  
          // Receive startup notification from the server
          fromServer.readInt(); // Whatever read is ignored
  
          // The other player has joined
          Platform.runLater(() -> 
            lblStatus.setText("Player 2 has joined. I start first"));
  
          // It is my turn
          myTurn = true;
        }
        else if (player == PLAYER2) {
          myToken = 'O';
          otherToken = 'X';
          Platform.runLater(() -> {
            lblTitle.setText("Player 2 with token 'O'");
            lblStatus.setText("Waiting for player 1 to move");
          });
        }
  
        // Continue to play
        while (continueToPlay) {      
          if (player == PLAYER1) {
            waitForPlayerAction(); // Wait for player 1 to move
            sendMove(); // Send the move to the server
            receiveInfoFromServer(); // Receive info from the server
          }
          else if (player == PLAYER2) {
            receiveInfoFromServer(); // Receive info from the server
            waitForPlayerAction(); // Wait for player 2 to move
            sendMove(); // Send player 2's move to the server
          }
        }
      }
      catch (Exception ex) {
        ex.printStackTrace();
      }
    }).start();
  }

  //  lasse  \\
  //this makes sure your app is sleeping while its not your turn
  private void waitForPlayerAction() throws InterruptedException {
    while (waiting) {
      Thread.sleep(100);
    }

    waiting = true;
  }

  //  lasse  \\
  //denne metode sender de indikeret valgte rekker og colonner
  private void sendMove() throws IOException {
    toServer.writeInt(rowSelected);
    toServer.writeInt(columnSelected);
  }

  //  lasse  \\
  // her finder programmet ud af om spillet slutter når der bliver taget et træk af en spiller
  private void receiveInfoFromServer() throws IOException {

    //  lasse  \\
    //den henter status fra server for at kunne se om spillet er slut
    int status = fromServer.readInt();


    //  lasse  \\
    //vis spiller et har vundet så slutter spillet
    if (status == PLAYER1_WON) {
      continueToPlay = false;
      if (myToken == 'X') {
        Platform.runLater(() -> lblStatus.setText("I won! (X)"));
      }
      else if (myToken == 'O') {
        Platform.runLater(() -> 
          lblStatus.setText("Player 1 (X) has won!"));
        receiveMove();
      }
    }


    //  lasse  \\
    //vis player to har vundet så slutter spillet
    else if (status == PLAYER2_WON) {
      continueToPlay = false;
      if (myToken == 'O') {
        Platform.runLater(() -> lblStatus.setText("I won! (O)"));
      }
      else if (myToken == 'X') {
        Platform.runLater(() -> 
          lblStatus.setText("Player 2 (O) has won!"));
        receiveMove();
      }
    }

    //  lasse  \\
    //vis brættet er fuldt og der ikke er nogen der har vundet så slutter spillet
    else if (status == DRAW) {
      continueToPlay = false;
      Platform.runLater(() -> 
        lblStatus.setText("Game is over, no winner!"));
      if (myToken == 'O') {
        receiveMove();
      }
    }

    //  lasse  \\
    //vis spillet ikke er slut så får jeg turen
    else {
      receiveMove();
      Platform.runLater(() -> lblStatus.setText("My turn"));
      myTurn = true; // It is my turn
    }
  }


  //  lasse  \\
  //når din modstander har lavet et træk så bliver dit bræt ændret samtidig
  private void receiveMove() throws IOException {
    int row = fromServer.readInt();
    int column = fromServer.readInt();
    Platform.runLater(() -> cell[row][column].setToken(otherToken));
  }

  //  lasse  \\
  //single responsability princippet siger at da det kun er vores clint server som skal bruge den her klasse så skal vi sikre at det kun er denne klasse som kender til
  public class Cell extends Pane {

    //  lasse  \\
    //vores celler skal have rækker og colloner
    private int row;
    private int column;

    //  lasse  \\
    //placeholder værdi
    private char token = ' ';

    //  lasse  \\
    //her definere vi at en celle skal bestå af en sort border en "størelse".
    //og til sidst sikre vi os at vis der bliver trykket på en celle så køre den metode som hedder handleMouseClick (det er defineret i handleMouseClick metoden at der kune sker noget vis det er din tur)
    public Cell(int row, int column) {
      this.row = row;
      this.column = column;
      this.setPrefSize(800, 800); //Lasse: jeg ved ikke helt hvordan den her linie virker men internettet sadge at det er nødvendigt for at kunne sikre sig at kunne sikre sig størelse unden brug a billeder
      setStyle("-fx-border-color: black");
      this.setOnMouseClicked(e -> handleMouseClick());  
    }

    //  lasse \\
    //en getter for the sake of having it jeg tror aldrig den bliver brugt
    public char getToken() {
      return token;
    }

    //  lasse  \\
    //denne setter er lavet om til at bruge vores repaint metode til at sette vores token
    public void setToken(char c) {
      token = c;
      repaint();
    }


        //  lasse  \\
    //denne metode bliver kaldt når en pane i vores board bliver trykket på men kun vis det er din tur\\
    //den tager udgangspunkt i hvad for et tegn du har fået givet til dig af server og gør sådan at du sætte et X ind vis du er player X eller omvendt vis du er player O\\
    protected void repaint() {
      if (token == 'X') {
        Line line1 = new Line(10, 10,
          this.getWidth() - 10, this.getHeight() - 10);
        line1.endXProperty().bind(this.widthProperty().subtract(10));
        line1.endYProperty().bind(this.heightProperty().subtract(10));
        Line line2 = new Line(10, this.getHeight() - 10, 
          this.getWidth() - 10, 10);
        line2.startYProperty().bind(
          this.heightProperty().subtract(10));
        line2.endXProperty().bind(this.widthProperty().subtract(10));

        this.getChildren().addAll(line1, line2); 
      }
      else if (token == 'O') {
        Ellipse ellipse = new Ellipse(this.getWidth() / 2, 
          this.getHeight() / 2, this.getWidth() / 2 - 10, 
          this.getHeight() / 2 - 10);
        ellipse.centerXProperty().bind(
          this.widthProperty().divide(2));
        ellipse.centerYProperty().bind(
            this.heightProperty().divide(2));
        ellipse.radiusXProperty().bind(
            this.widthProperty().divide(2).subtract(10));        
        ellipse.radiusYProperty().bind(
            this.heightProperty().divide(2).subtract(10));   
        ellipse.setStroke(Color.BLACK);
        ellipse.setFill(Color.WHITE);
        
        getChildren().add(ellipse);
      }
    }


    //  lasse  \\
    //her handler vi vores mouse event, når vi clicker på en pane
    //og det virker kun vis det er din tur
    private void handleMouseClick() {
      if (token == ' ' && myTurn) {
        setToken(myToken);
        myTurn = false;
        rowSelected = row;
        columnSelected = column;
        lblStatus.setText("Waiting for the other player to move");
        waiting = false;
      }
    }
  }

}
