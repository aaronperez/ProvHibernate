package com.aaron.provhibernate;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.LoaderManager;
import android.content.ContentValues;
import android.content.CursorLoader;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.Loader;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicHeader;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;


public class Main extends ActionBarActivity implements LoaderManager.LoaderCallbacks<Cursor>{

    private ListView lv;
    private Adaptador ad;
    private int index;
    private final int ACTIVIDAD_ELIMINAR = 1;
    public View row;
    private SharedPreferences prefs;
    private SharedPreferences.Editor editor;
    private Cursor cursor;
    private final String URLCONTROL="http://localhost/InmobiliariaH/control";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        try {
            initComponent();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.agregar) {
            edicion(index, "add");
            return true;
        }
        if (id == R.id.sincronizar) {
            sincronizar();
            return true;
        }
        if (id == R.id.usuario) {
            AlertDialog.Builder alert= new AlertDialog.Builder(this);
            alert.setTitle(R.string.usuarioCambiar);
            LayoutInflater inflater= LayoutInflater.from(this);
            final View vista = inflater.inflate(R.layout.usuario, null);
            alert.setView(vista);
            alert.setPositiveButton(R.string.editar, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    EditText et = (EditText) vista.findViewById(R.id.etUsuario);
                    if(et.getText().toString().isEmpty()){
                        tostada(R.string.mensajeCancelar);
                    }else{
                        editor = prefs.edit();
                        editor.putString("usuario", et.getText().toString());
                        editor.commit();
                        tostada(R.string.usuarioCambiado);
                    }
                }
            });
            alert.setNegativeButton(R.string.cancelar,new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    tostada(R.string.mensajeCancelar);
                    dialog.dismiss();
                }
            });
            alert.show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /* Desplegar menú contextual*/
    @Override
    public void onCreateContextMenu(ContextMenu main, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(main, v, menuInfo);
        MenuInflater inflater= getMenuInflater();
        inflater.inflate(R.menu.contextual, main);
    }

    /* Al seleccionar elemento del menú contextual */
    @Override
    public boolean onContextItemSelected(MenuItem item) {
        int id=item.getItemId();
        AdapterView.AdapterContextMenuInfo info= (AdapterView.AdapterContextMenuInfo)item.getMenuInfo();
        int index= info.position;
        Cursor cursor = (Cursor)lv.getItemAtPosition(index);
        int idInmueble= cursor.getInt(0);
        if (id == R.id.action_editar) {
            edicion(idInmueble, "edit");
            return true;
        }else if (id == R.id.action_eliminar) {
            edicion(idInmueble, "delete");
            return true;
        }
        return super.onContextItemSelected(item);
    }

    public void initComponent()throws IOException {
        // Iniciamos las preferencias compartidas
        prefs=this.getSharedPreferences("usuario", 0);
        ad = new Adaptador(this, null);
        lv = (ListView) findViewById(R.id.lvLista);
        lv.setAdapter(ad);
        registerForContextMenu(lv);
        getLoaderManager().initLoader(0, null, this);
        final Detalle fdetalle=(Detalle)getFragmentManager().findFragmentById(R.id.fDetalle);
        final boolean horizontal=fdetalle!=null && fdetalle.isInLayout();
        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int posicion, long id) {
                if(horizontal){
                    if (row != null) {
                        row.setBackgroundResource(R.color.blanco);
                    }
                    row = view;
                    view.setBackgroundResource(R.color.secundario);
                    fdetalle.inicia(id+"");
                }
                else{
                    Intent intent=new Intent(Main.this, Actividad.class);
                    intent.putExtra("id", id+"");
                    startActivity(intent);
                }
            }
        });

    }

    /***********Activities************/
    @Override
    public void onActivityResult(int requestCode,int resultCode, Intent data){
        if (resultCode== Activity.RESULT_OK) {
            switch (requestCode){
                case ACTIVIDAD_ELIMINAR:
                    index= data.getIntExtra("index",0);
                    Uri uri = Contrato.TablaInmueble.CONTENT_URI;
                    String where = Contrato.TablaInmueble._ID +" = ? ";
                    String args[] = new String[]{index+""};
                    getContentResolver().delete(uri,where,args);
                    break;
            }
        }
        else{
            tostada(R.string.mensajeCancelar);
        }
    }

    /* Mostramos un mensaje flotante a partir de un recurso string*/
    public void tostada(int s){
        Toast.makeText(this, getText(s), Toast.LENGTH_SHORT).show();
    }

    /*        Menús          */
    /*************************/
    public void edicion(int index,String opcion) {
        Intent i;
        Bundle b = new Bundle();
        b.putString("opcion", opcion);
        b.putInt("index", index);
        if (opcion.equals("delete")) {
            i = new Intent(this, Eliminar.class);
            i.putExtras(b);
            startActivityForResult(i, ACTIVIDAD_ELIMINAR);
        } else {
            i = new Intent(this, Edicion.class);
            i.putExtras(b);
            startActivity(i, b);
        }
    }


    /*  Clases para el Callback del CP */
    /* ******************************* */
    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        //cargar el cursor del content provider
        Uri uri = Contrato.TablaInmueble.CONTENT_URI;
        return new CursorLoader( this, uri, null, null, null, Contrato.TablaInmueble.LOCALIDAD +" collate localized asc");
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        //Necesitamos un adaptador
        ad.swapCursor(data);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        ad.swapCursor(null);
    }

    /* ********** Subir ficheros y sincronizar *********** */
    private boolean sincronizar(){
        Uri uri = Contrato.TablaInmueble.CONTENT_URI;
        String[] projection = new String[]{Contrato.TablaInmueble.LOCALIDAD,
                Contrato.TablaInmueble.TIPO,
                Contrato.TablaInmueble.PRECIO,
                Contrato.TablaInmueble.DIRECCION,
                Contrato.TablaInmueble.SUBIDO,
                Contrato.TablaInmueble._ID};
        String seleccion = Contrato.TablaInmueble.SUBIDO + " = ?";
        String[] parametrosSeleccion = new String[]{"0"};
        cursor = this.getContentResolver().query(uri, projection, seleccion,
                parametrosSeleccion, null);
        if(cursor.getCount()>0) {
            int i = 0;
            cursor.moveToFirst();
            do {
                JSONObject objetoJSON = new JSONObject();
                try {
                    objetoJSON.put("localidad", cursor.getString(0) + "");
                    objetoJSON.put("tipo", cursor.getString(1));
                    objetoJSON.put("precio", cursor.getInt(2) + "");
                    objetoJSON.put("calle", cursor.getString(3));
                    objetoJSON.put("numero", cursor.getString(4));
                    objetoJSON.put("usuario", this.getSharedPreferences("usuario", 0));
                    subirInmueble(objetoJSON, cursor.getString(6));
                } catch (JSONException e) {
                }
                i++;
            } while (cursor.moveToNext());
        }else{
            Toast.makeText(this, getString(R.string.subidos), Toast.LENGTH_SHORT).show();
        }
        return true;
    }

    private void subirInmueble(final JSONObject objetoJSON, final String idOriginal){
        Thread s = new Thread(){
            @Override
            public synchronized void run() {
                String res = postData(URLCONTROL+"?action=inmueble", objetoJSON);
                long id = -1;
                if(res!=null) {
                    try {
                        JSONObject jsonObj = new JSONObject(res);
                        String ide = jsonObj.getString("idinmueble");
                        id = Long.parseLong(ide);
                    } catch (Exception e) {
                        id = -1;
                    }
                }else{
                    Main.this.runOnUiThread(new Runnable() {
                        public void run() {
                            Toast.makeText(Main.this, getString(R.string.errorS), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
                if(id > 0){
                    ContentValues valores;
                    Uri uri = Contrato.TablaInmueble.CONTENT_URI;
                    valores = consVal(objetoJSON);
                    if(valores!=null) {
                        String where = Contrato.TablaInmueble._ID + " = ?";
                        String[] args = new String[]{idOriginal};
                        getContentResolver().update(uri, valores, where, args);
                        subirFotos(idOriginal, id+"");
                    }else{
                        Log.v("mio", "error");
                    }
                }
            }
        };
        s.start();
    }

    private ContentValues consVal(JSONObject o){
        ContentValues valores = new ContentValues();

        try {
            valores.put(Contrato.TablaInmueble.LOCALIDAD, o.getString("localidad"));
            valores.put(Contrato.TablaInmueble.TIPO, o.getString("tipo"));
            valores.put(Contrato.TablaInmueble.PRECIO, o.getString("precio"));
            valores.put(Contrato.TablaInmueble.DIRECCION, o.getString("direccion"));
            valores.put(Contrato.TablaInmueble.SUBIDO, 1);
        } catch (JSONException e) {
            valores = null;
        }
        return valores;
    }

    private void subirFotos(final String idOriginal, final String id){
        File[] array;
        try {
            array = getExternalFilesDir(idOriginal + "/").listFiles();
        } catch (NullPointerException e){
            array = null;
        }
        final ArrayList<String> fotos = new ArrayList<>();
        if(array != null && array.length >0){
            for(File a : array){
                if(a.getPath().contains("inmueble_"+idOriginal+"_")){
                    fotos.add(a.getPath());
                }
            }
        }
        for (int i = 0; i < fotos.size(); i++) {
            final int finalI = i;
            Thread t = new Thread(){
                @Override
                public synchronized void run() {
                    String res =postFile(URLCONTROL+"?action=fichero", "archivo", fotos.get(finalI), id);
                    if(res == null)
                        Toast.makeText(Main.this, getString(R.string.errorF), Toast.LENGTH_SHORT).show();
                }
            };
            t.start();
        }
    }

    /* ****** Métodos POST ******* */

    private String postData(String url, JSONObject obj) {
        HttpParams parametros = new BasicHttpParams();
        HttpConnectionParams.setConnectionTimeout(parametros, 10000);
        HttpConnectionParams.setSoTimeout(parametros, 10000);
        HttpClient httpclient = new DefaultHttpClient(parametros);
        String json = obj.toString();

        try {
            HttpPost httppost = new HttpPost(url.toString());
            httppost.setHeader("Content-type", "application/json");

            StringEntity se = new StringEntity(json);
            se.setContentEncoding(new BasicHeader(HTTP.CONTENT_TYPE, "application/json"));
            httppost.setEntity(se);

            HttpResponse response = httpclient.execute(httppost);
            String temp = EntityUtils.toString(response.getEntity());
            return temp;
        } catch (ClientProtocolException e) {
            return null;
        } catch (IOException e) {
            return null;
        }
    }

    private String postFile(String urlPeticion, String nombreParametro, String nombreArchivo, String idInmueble) {
        String resultado="";
        try {
            URL url = new URL(urlPeticion);
            HttpURLConnection conexion = (HttpURLConnection) url.openConnection();
            conexion.setDoOutput(true);
            conexion.setRequestMethod("POST");
            FileBody fileBody = new FileBody(new File(nombreArchivo));
            MultipartEntity multipartEntity = new MultipartEntity(HttpMultipartMode.STRICT);
            multipartEntity.addPart(nombreParametro, fileBody);
            multipartEntity.addPart("idinmueble", new StringBody(idInmueble));
            multipartEntity.addPart("nombre", new StringBody(nombreArchivo));
            conexion.setRequestProperty("Content-Type", multipartEntity.getContentType().getValue());
            OutputStream out = conexion.getOutputStream();
            try {
                multipartEntity.writeTo(out);
            } finally {
                out.close();
            }
            BufferedReader in = new BufferedReader(new InputStreamReader(conexion.getInputStream()));
            String decodedString;
            while ((decodedString = in.readLine()) != null) {
                resultado+=decodedString+"\n";
            }
            in.close();
        } catch (MalformedURLException ex) {
            return null;
        } catch (IOException ex) {
            return null;
        }
        return resultado;
    }

}
