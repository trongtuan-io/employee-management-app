package com.example.employee_management_app;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Common HR presentation components. No business rules or API changes here. */
public final class HrUi {
    private HrUi() { }
    public interface Done { void receive(Object data); }
    private static final int BACKGROUND=Color.rgb(247,248,252);
    private static final int INK=Color.rgb(30,41,59);
    private static final int MUTED=Color.rgb(100,116,139);
    private static final int PURPLE=Color.rgb(79,70,229);
    private static final int LINE=Color.rgb(225,231,240);

    public static int dp(Context ctx,float value){return (int)(value*ctx.getResources().getDisplayMetrics().density+0.5f);}
    public static GradientDrawable shape(Context ctx,int fill,int border,int radius){
        GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(ctx,radius));
        if(border!=0)d.setStroke(dp(ctx,1),border);
        return d;
    }
    public static LinearLayout page(AppCompatActivity activity,String title){
        activity.setTitle(title);
        activity.getWindow().setStatusBarColor(BACKGROUND);
        activity.getWindow().getDecorView().setSystemUiVisibility(
            activity.getWindow().getDecorView().getSystemUiVisibility()|View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        ScrollView sv=new ScrollView(activity);
        sv.setBackgroundColor(BACKGROUND);sv.setFillViewport(true);sv.setClipToPadding(false);
        sv.setOnApplyWindowInsetsListener((view,insets)->{
            sv.setPadding(0,insets.getSystemWindowInsetTop(),0,insets.getSystemWindowInsetBottom());
            return insets;
        });
        LinearLayout root=new LinearLayout(activity);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(activity,18),dp(activity,12),dp(activity,18),dp(activity,28));
        sv.addView(root,new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
        activity.setContentView(sv);
        sv.requestApplyInsets();
        // Standard header row: small "Quay lai" button + centered title (same pattern as XML screens).
        LinearLayout header=new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);
        hp.setMargins(0,dp(activity,6),0,dp(activity,12));
        root.addView(header,hp);
        Button back=new Button(activity);
        back.setText("Quay lại");back.setAllCaps(false);back.setTextSize(13);
        back.setTypeface(null,Typeface.BOLD);
        back.setMinimumHeight(0);back.setMinHeight(0);
        back.setPadding(dp(activity,12),dp(activity,6),dp(activity,12),dp(activity,6));
        back.setTextColor(PURPLE);
        back.setBackground(shape(activity,Color.WHITE,LINE,11));
        back.setOnClickListener(v->activity.finish());
        header.addView(back);
        TextView t=new TextView(activity);
        t.setText(title);t.setTextSize(18);t.setTextColor(INK);t.setTypeface(null,Typeface.BOLD);
        t.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1);
        tp.setMargins(dp(activity,8),0,dp(activity,8),0);
        header.addView(t,tp);
        return root;
    }
    public static TextView label(LinearLayout root,String text){
        TextView t=new TextView(root.getContext());t.setText(text);t.setTextSize(14);
        t.setTextColor(MUTED);t.setLineSpacing(dp(root.getContext(),2),1f);
        t.setPadding(0,dp(root.getContext(),6),0,dp(root.getContext(),8));root.addView(t);return t;
    }
    public static TextView title(LinearLayout root,String text){
        TextView t=new TextView(root.getContext());t.setText(text);t.setTextColor(INK);
        t.setTextSize(16);t.setTypeface(null,Typeface.BOLD);
        t.setPadding(0,0,0,dp(root.getContext(),5));root.addView(t);return t;
    }
    public static void section(LinearLayout root,String text){
        TextView t=new TextView(root.getContext());t.setText(text);t.setTextSize(16);
        t.setTextColor(INK);t.setTypeface(null,Typeface.BOLD);
        t.setPadding(0,dp(root.getContext(),14),0,dp(root.getContext(),8));root.addView(t);
    }
    public static LinearLayout card(LinearLayout root){
        Context c=root.getContext();LinearLayout box=new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(c,15),dp(c,14),dp(c,15),dp(c,14));
        box.setBackground(shape(c,Color.WHITE,LINE,16));box.setElevation(dp(c,1));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(0,dp(c,4),0,dp(c,10));root.addView(box,p);return box;
    }
    public static TextView status(LinearLayout root,String text,boolean positive){
        TextView v=new TextView(root.getContext());v.setText(text);v.setTextSize(12);
        v.setTypeface(null,Typeface.BOLD);
        v.setTextColor(positive?Color.rgb(22,101,52):Color.rgb(107,70,193));
        v.setPadding(dp(root.getContext(),10),dp(root.getContext(),6),dp(root.getContext(),10),dp(root.getContext(),6));
        v.setBackground(shape(root.getContext(),positive?Color.rgb(220,252,231):Color.rgb(237,233,254),0,9));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(0,dp(root.getContext(),7),0,dp(root.getContext(),6));root.addView(v,p);return v;
    }
    public static EditText input(LinearLayout root,String hint){
        Context c=root.getContext();EditText e=new EditText(c);
        e.setSingleLine(true);e.setTextSize(14);e.setTextColor(INK);e.setHintTextColor(Color.rgb(148,163,184));
        e.setHint(hint);e.setPadding(dp(c,13),0,dp(c,13),0);e.setMinHeight(dp(c,48));
        e.setBackground(shape(c,Color.WHITE,LINE,11));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(c,48));
        p.setMargins(0,dp(c,5),0,dp(c,10));root.addView(e,p);return e;
    }
    private static Button action(LinearLayout root,String text,Runnable handler,boolean primary,boolean dangerous){
        Context c=root.getContext();Button b=new Button(c);
        b.setText(text);b.setAllCaps(false);b.setTextSize(14);b.setTypeface(null,Typeface.BOLD);
        b.setMinimumHeight(0);b.setMinHeight(0);
        b.setPadding(dp(c,12),0,dp(c,12),0);
        b.setTextColor(primary?Color.WHITE:(dangerous?Color.rgb(185,28,28):PURPLE));
        b.setBackground(shape(c,primary?PURPLE:Color.WHITE,primary?0:LINE,11));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(c,46));
        p.setMargins(0,dp(c,5),0,dp(c,5));root.addView(b,p);
        b.setOnClickListener(v->handler.run());return b;
    }
    public static Button button(LinearLayout root,String text,Runnable handler){return action(root,text,handler,true,false);}
    public static Button secondary(LinearLayout root,String text,Runnable handler){return action(root,text,handler,false,false);}
    public static Button danger(LinearLayout root,String text,Runnable handler){return action(root,text,handler,false,true);}
    public static void navCard(LinearLayout root,String title,String caption,Runnable handler){
        LinearLayout box=card(root);
        LinearLayout header=new LinearLayout(root.getContext());header.setGravity(Gravity.CENTER_VERTICAL);
        box.addView(header,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout texts=new LinearLayout(root.getContext());texts.setOrientation(LinearLayout.VERTICAL);
        header.addView(texts,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        title(texts,title);label(texts,caption);
        TextView arrow=new TextView(root.getContext());arrow.setText("›");arrow.setTextColor(PURPLE);arrow.setTextSize(28);
        arrow.setPadding(dp(root.getContext(),10),0,0,0);header.addView(arrow);
        box.setClickable(true);box.setFocusable(true);box.setContentDescription(title+". "+caption);
        box.setOnClickListener(v->handler.run());
    }
    public static HrApi api(Context ctx){return ApiClient.getClient(ctx).create(HrApi.class);}
    @SuppressWarnings("unchecked") public static Map<String,Object> map(Object obj){
        if(!(obj instanceof Map))return java.util.Collections.emptyMap();return (Map<String,Object>)obj;
    }
    public static List<Map<String,Object>> rows(Object obj){
        List<Map<String,Object>> r=new ArrayList<>();
        if(obj instanceof List)for(Object i:(List<?>)obj)if(i instanceof Map)r.add(map(i));return r;
    }
    public static String s(Object obj){return obj==null?"":String.valueOf(obj);}
    public static String id(Object obj){
        if(obj==null)return "";
        if(obj instanceof Number)return String.valueOf(((Number)obj).longValue());
        String value=s(obj);return value.matches("\\d+\\.0")?value.substring(0,value.length()-2):value;
    }
    public static int n(Object obj){try{return (int)Double.parseDouble(s(obj));}catch(Exception e){return 0;}}
    public static void call(AppCompatActivity activity,Call<Map<String,Object>> request,Done ok){
        request.enqueue(new Callback<Map<String,Object>>() {
            @Override public void onResponse(Call<Map<String,Object>> c,Response<Map<String,Object>> response){
                if(activity.isFinishing()||activity.isDestroyed())return;
                Map<String,Object> body=response.body();
                if(!response.isSuccessful()||body==null||!Boolean.TRUE.equals(body.get("success"))){
                    String error="HTTP "+response.code();
                    if(response.errorBody()!=null){try{
                        ApiResponse apiErr=new com.google.gson.Gson().fromJson(response.errorBody().string(),ApiResponse.class);
                        if(apiErr!=null&&apiErr.getMessage()!=null)error=apiErr.getMessage();
                    }catch(Exception ignored){}}
                    android.widget.Toast.makeText(activity,error,android.widget.Toast.LENGTH_LONG).show();return;
                }
                ok.receive(body.get("data"));
            }
            @Override public void onFailure(Call<Map<String,Object>> c,Throwable t){
                if(!activity.isFinishing())android.widget.Toast.makeText(activity,"Lỗi mạng: "+t.getMessage(),android.widget.Toast.LENGTH_LONG).show();
            }
        });
    }
    public static boolean hr(Context ctx){
        TokenManager tm=TokenManager.getInstance(ctx);
        return "HR".equalsIgnoreCase(tm.getDepartmentName())&&
                ("manager".equalsIgnoreCase(tm.getRole())||"staff".equalsIgnoreCase(tm.getRole()));
    }
    public static boolean managerHr(Context ctx){return hr(ctx)&&"manager".equalsIgnoreCase(TokenManager.getInstance(ctx).getRole());}
}
