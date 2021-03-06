package main;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;

import GUI.Window;

public class ClienteThread extends Thread {
	private Socket s;
	private Window w;
	private ArrayList<String> cabeceraEntrante;
	private String rootDirectory = "public_html/";
	private String errorDirectory = "errors/";
	private String logsDirectory = "logs/";
	private String[] methods = {"HEAD","GET","POST","PUT","DELETE","TRACE","OPTIONS","CONNECT"};
	private ServerLog log;
	/*---PREPARAR PROTECCION---*/
	private AuthBasic protect = new AuthBasic();

	
	public ClienteThread(Socket s, Window w) {
		this.s=s;
		this.w = w;
	}

	@Override
	public void run() {
		/*---PREPARAR FLUJOS---*/
		BufferedReader reader = null;
		PrintWriter writer = null;
		cabeceraEntrante = new ArrayList<String>();

		/*---PREPARAR LOG---*/
		log = new ServerLog(logsDirectory);
		
		String FirstLine;
		try {
			/*--GESTIONAR PETICION CLIENTE---*/
			w.book.setText(w.book.getText() + "Atendiendo cliente...\n");
			reader = new BufferedReader(
					new InputStreamReader(s.getInputStream()));
			
			String msgCliente;
			
			
			//Se crea el try para controlar los errores de cabeceras null
			try{
				while(!((msgCliente=reader.readLine()).equalsIgnoreCase(""))){
					cabeceraEntrante.add(msgCliente); //Metemos la cabecera recibida en un ArrayList
				}
			}catch(NullPointerException e){
				w.book.setText(w.book.getText() + "Se ha recibido una cabecera vacia desde: " + s.getRemoteSocketAddress() + "\n");
			}

			//Comprobamos que la cabecera esta bien formada (CODE 400). Si lo est�, respondemos la peticion
			if(verifyHeader(cabeceraEntrante)){
				
				FirstLine = cabeceraEntrante.get(0);
				String [] peticion=FirstLine.split(" "); //Recuperamos las peticiones de cliente
				
				switch (getMethod(peticion[0])) {
				case 1: //El metodo es GET
					String webRequired = peticion[1];
					File web,carp; 
					String path;
					
					carp = new File(rootDirectory + webRequired); //Creamos este file para comprobar si es directorio

					if(carp.isDirectory() && !(webRequired.lastIndexOf("/")==webRequired.length())){ //Si no hay "/" al final la a�adimos
						path = rootDirectory + webRequired + "/" + "index.html";
						web = new File(path);
					}else if(carp.isDirectory()){
						path = rootDirectory + webRequired + "index.html";
						web = new File(path);
					}
					else{
						path = rootDirectory + webRequired;
						web = new File(path);
					}
					
					log.saveMsg("GET " + path + " from " + s.getInetAddress());//Guardamos evento en el log
					writer = new PrintWriter(s.getOutputStream(),true); //Buffer de salida de texto plano
					DataOutputStream out = new DataOutputStream(s.getOutputStream()); //Buffer salida binarios
					
					/*---PROTECCION BASIC DE DIRECTORIOS---*/
					if((protect.isProtected(carp) || protect.isProtected(new File(web.getParent()))) 
							&& !envioAuth(cabeceraEntrante) && !(getExtension(path).equalsIgnoreCase("drkwb"))){

						String webPlano = readFileAsString(errorDirectory + "401.html");
						String headerResp = makeHeader(401,webPlano.length(),"text/html"); //Construimos cabecera para web
						
						writer.println(headerResp + webPlano);
						
					}else if((protect.isProtected(carp) || protect.isProtected(new File(web.getParent())))
							&& envioAuth(cabeceraEntrante) && !(getExtension(path).equalsIgnoreCase("drkwb"))){
						//Comprobamos credenciales
						if(protect.comprobarCredenciales(cabeceraEntrante,web)){
							String webPlano = readFileAsString(path); //Pasamos el file a String
							String headerResp = makeHeader(200,webPlano.length(),getMime(path)); //Construimos cabecera para web
							
							writer.println(headerResp + webPlano);
						}else{ //Si las credenciales son erroneas, mandamos header 401 de nuevo
							String webPlano = readFileAsString(errorDirectory + "401.html"); //Pasamos el file a String
							String headerResp = makeHeader(401,webPlano.length(),"text/html"); //Construimos cabecera para web
							
							writer.println(headerResp + webPlano);
							
							log.saveMsg("Fail to Auth to " + web.getPath() + " from " + s.getInetAddress());
						}
						
					}else{ //Si la carpeta no esta protegida la servimos
						
						if(web.exists() && getExtension(path).equalsIgnoreCase("drkwb")){ //Archivos protegidos
				        	String webPlano = readFileAsString(errorDirectory + "403.html");
							String headerResp = makeHeader(403,webPlano.length(),"text/html");
			
							writer.println(headerResp + webPlano);
				        	
				        }else if(web.exists() && (getMime(path).equalsIgnoreCase("text/html") || getMime(path).equalsIgnoreCase("text/css"))){
							/*---GESTIONAMOS LA RESPUESTA DEL SERVIDOR SI LA WEB EXISTE---*/
							String webPlano = readFileAsString(path); //Pasamos el file a String
							String headerResp = makeHeader(200,webPlano.length(),getMime(path)); //Construimos cabecera para web
							
							writer.println(headerResp + webPlano);
						}else if(web.exists() && (getMime(path).equalsIgnoreCase("image/png") || getMime(path).equalsIgnoreCase("image/jpeg") || getMime(path).equalsIgnoreCase("application/pdf") || getMime(path).equalsIgnoreCase("application/xml") 
								|| getMime(path).equalsIgnoreCase("text/plain") || getMime(path).equalsIgnoreCase("image/bmp") || getMime(path).equalsIgnoreCase("image/gif") || getMime(path).equalsIgnoreCase("application/zip")
								|| getMime(path).equalsIgnoreCase("application/x-compressed") || getMime(path).equalsIgnoreCase("application/octet-stream") || getMime(path).equalsIgnoreCase("audio/mpeg3"))){
							
							/*---GESTIONAMOS LA RESPUESTA DEL SERVIDOR SI el BINARIO EXISTE---*/

							out.writeBytes(makeHeader(200, (int) web.length(), getMime(path)));
							out.flush();
							
							InputStream is =  new FileInputStream(path);
				            BufferedInputStream bis = new BufferedInputStream(is);
				 
				            int ch;
				            while ((ch = bis.read()) != -1){
				            	out.write(ch);
				            }
				            out.writeBytes("\n");
				            out.flush();
				            
				            out.close();
				            is.close();
				            bis.close();
				        }else if(!web.exists()){
				        	/*---GESTIONAMOS LA RESPUESTA DEL SERVIDOR SI LA WEB NO EXISTE---*/
				        	String webPlano = readFileAsString(errorDirectory + "404.html");
							String headerResp = makeHeader(404,webPlano.length(),"text/html");
			
							writer.println(headerResp + webPlano);
						}
					}
					break;
				case 2: // El metodo es POST
					
					break;
				default:
					break;
				}
			}else{
				writer = new PrintWriter(s.getOutputStream(),true);
	        	String webPlano = readFileAsString(errorDirectory + "400.html");
				String headerResp = makeHeader(400,webPlano.length(),"text/html");

				writer.println(headerResp + webPlano);
				
				log.saveMsg("Malformed header from " + s.getInetAddress());//Cabecera mal formada
			}
			
		} catch (IOException e) {
			//En caso de error al gestionar la peticion, se envia un codigo HTTP 500
			System.err.println("Se ha recibido una cabecera vacia desde: " + s.getRemoteSocketAddress());
			log.saveMsg("Empty header from " + s.getInetAddress());
			try{
	        	String webPlano = readFileAsString(errorDirectory + "500.html");
				String headerResp = makeHeader(500,webPlano.length(),"text/html");
	
				writer.println(headerResp + webPlano);
				e.printStackTrace();
			}catch(IOException ex){
				System.err.println("No se le pudo comunicar el error al cliente");
			}
			
		}finally{
			try {
				if(reader!=null){
					reader.close();
				}
				if(writer!=null){
					writer.close();
				}
				if(s!=null){
					s.close();
				}
			} catch (IOException e) {
				System.err.println("Error al cerrar sockets");
				log.saveMsg("Internal socket error");
			}
		}
	}

	//Metodo encargado de comprobar que la cabecera recibida esta bien formada (BETA)
	private boolean verifyHeader(ArrayList<String> cabecera) {
		try{
			String FirstLine = cabeceraEntrante.get(0);
			String [] peticion=FirstLine.split(" ");
			
			for(int i = 0;i<methods.length;i++){
				if(peticion[0].equalsIgnoreCase(methods[i])){
					return true;
				}
			}
			return false;
		}catch(IndexOutOfBoundsException e){
			System.err.println("Imposible verificar cabecera, probablemente vac�a");
			log.saveMsg("Impossible to verify header");
		}
		return false;
	}
	
	//Metodo para obtener el tipo mime del archivo solicitado
	private String getMime(String path) {
		
		String extension = getExtension(path);
		
		if(extension.equalsIgnoreCase("html")){
			return "text/html";
		}else if(extension.equalsIgnoreCase("jpg")){
			return "image/jpeg";
		}else if(extension.equalsIgnoreCase("png")){
			return "image/png";
		}else if(extension.equalsIgnoreCase("pdf")){
			return "application/pdf";
		}else if(extension.equalsIgnoreCase("xml")){
			return "application/xml";
		}else if(extension.equalsIgnoreCase("c") || extension.equalsIgnoreCase("cc") || extension.equalsIgnoreCase("com") || extension.equalsIgnoreCase("conf") || extension.equalsIgnoreCase("def") || extension.equalsIgnoreCase("h") 
				|| extension.equalsIgnoreCase("java") || extension.equalsIgnoreCase("log") || extension.equalsIgnoreCase("pl") || extension.equalsIgnoreCase("txt")){
			return "text/plain";
		}else if(extension.equalsIgnoreCase("bmp")){
			return "image/bmp";
		}else if(extension.equalsIgnoreCase("gif")){
			return "image/gif";
		}else if(extension.equalsIgnoreCase("zip")){
			return "application/zip";
		}else if(extension.equalsIgnoreCase("tgz") || extension.equalsIgnoreCase("gz")){
			return "application/x-compressed";
		}else if(extension.equalsIgnoreCase("exe")){
			return "application/octet-stream";
		}else if(extension.equalsIgnoreCase("mp3")){
			return "audio/mpeg3";
		}else if(extension.equalsIgnoreCase("css")){
			return "text/css";
		}
		
		return "text/plain"; //Si no encontramos el tipo mime lo mandamos como texto plano
	}

	//Metodo encargado de contruir Strings de cabeceras bien formadas
	private String makeHeader(int status,int length,String mime) {
		String header = null;
		
		switch (status) {
		case 200:
				header = "HTTP/1.1 " + status + " OK\n";
				break;
		case 400:
				header = "HTTP/1.1 " + status + "  Bad Request\n";
				break;
		case 401:
				header = "HTTP/1.1 " + status + "  Unauthorized\n";
				header += "WWW-Authenticate: Basic realm=\"" + protect.getAuthMessage() + "\"\n";
				break;
		case 403:
				header = "HTTP/1.1 " + status + "  Forbidden\n";
				break;
		case 404:
				header = "HTTP/1.1 " + status + " Not Found\n";
				break;
		case 500:
				header = "HTTP/1.1 " + status + " Internal Server Error\n";
				break;
		default:
			//Nunca llegaremos aqui
			break;
		}
		
		//Parte comun a todas las cabeceras
		header += "Server:	HTTPJava SERVER DRKWB\n";
		header += "Content-Type: " + mime + "\n";
		header += "Content-Length: " + length + "\n";
		header += "\n";
		return header;
		
	}
	
	//Metodo encargado de obtener el metodo enviado por el cliente
	private int getMethod(String peticion ){
		if(peticion.equalsIgnoreCase("GET")){
			return 1;
		}else if(peticion.equalsIgnoreCase("POST")){
			return 2;
		}
		return 0;
	}
	
	//Metodo encargado de transformar un File a String para poder ser enviado
	private String readFileAsString(String filePath) throws IOException {
        StringBuffer fileData = new StringBuffer();
        BufferedReader reader = new BufferedReader(
                new FileReader(filePath));
        char[] buf = new char[1024];
        int numRead=0;
        while((numRead=reader.read(buf)) != -1){
            String readData = String.valueOf(buf, 0, numRead);
            fileData.append(readData);
        }
        reader.close();
        return fileData.toString();
    }
	
	//Metodo encargado de obtener la extension del archivo a servir
	 public String getExtension(String filename) {
         int index = filename.lastIndexOf('.');
         if (index == -1) {
               return "";
         } else {
               return filename.substring(index + 1);
         }
	 }
	 
	 //Metodo encargado de comprobar si la cabecera entrante contiene autenticacion basic
	 private boolean envioAuth(ArrayList<String> cabecera) {
			for(int i = 0;i<cabecera.size();i++){
				if(cabecera.get(i).startsWith("Authorization: Basic")){
					return true; //Si contiene esa linea de cabecera, el cliente manda credenciales
				}
			}
			return false;
		}
	
	
}
