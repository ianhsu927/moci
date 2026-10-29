package dev.moci.ink;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.widget.Button;

final class BarButton extends Button {
    private final String icon;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    BarButton(Context context,String icon,String description){
        super(context);this.icon=icon;setContentDescription(description);
        setBackgroundColor(Color.TRANSPARENT);setStateListAnimator(null);
        setMinWidth(0);setMinimumWidth(0);setMinHeight(0);setMinimumHeight(0);setPadding(0,0,0,0);
    }
    @Override protected void onDraw(Canvas canvas){
        float size=26*getResources().getDisplayMetrics().density;
        canvas.save();canvas.translate((getWidth()-size)/2,(getHeight()-size)/2);canvas.scale(size/24,size/24);
        paint.setColor(isEnabled()?Color.BLACK:0xffb7b7b7);paint.setStrokeWidth(isSelected()?2.5f:1.9f);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);
        Path p=new Path();
        if("up".equals(icon)||"down".equals(icon)){
            boolean up="up".equals(icon);p.moveTo(6,up?15:9);p.lineTo(12,up?9:15);p.lineTo(18,up?15:9);canvas.drawPath(p,paint);
        }else if("lookup".equals(icon)){
            p.moveTo(12,5);p.cubicTo(8,2,4,3,2,4);p.lineTo(2,20);p.cubicTo(6,18,9,19,12,21);
            p.cubicTo(15,19,18,18,22,20);p.lineTo(22,4);p.cubicTo(18,3,15,2,12,5);p.lineTo(12,21);canvas.drawPath(p,paint);
        }else if("ai".equals(icon)){
            p.moveTo(10,3);p.lineTo(12.5f,9);p.lineTo(19,12);p.lineTo(12.5f,15);p.lineTo(10,21);p.lineTo(7.5f,15);p.lineTo(1,12);p.lineTo(7.5f,9);p.close();canvas.drawPath(p,paint);
            canvas.drawLine(19,2,19,6,paint);canvas.drawLine(17,4,21,4,paint);
        }else if("saved".equals(icon)){
            p.moveTo(12,21);p.cubicTo(8,18,2,13,2,8);p.cubicTo(2,2,9,1,12,6);p.cubicTo(15,1,22,2,22,8);p.cubicTo(22,13,16,18,12,21);canvas.drawPath(p,paint);
        }else{
            canvas.drawCircle(12,12,9,paint);canvas.drawLine(12,6,12,12,paint);canvas.drawLine(12,12,17,15,paint);
        }
        canvas.restore();
    }
}
