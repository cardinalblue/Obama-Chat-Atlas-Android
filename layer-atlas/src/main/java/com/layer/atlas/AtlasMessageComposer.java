/*
 * Copyright (c) 2015 Layer. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.layer.atlas;

import java.util.ArrayList;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import com.koushikdutta.async.future.FutureCallback;
import com.koushikdutta.ion.Ion;
import com.layer.sdk.LayerClient;
import com.layer.sdk.exceptions.LayerException;
import com.layer.sdk.listeners.LayerTypingIndicatorListener;
import com.layer.sdk.messaging.Conversation;
import com.layer.sdk.messaging.Message;
import com.layer.sdk.messaging.MessagePart;

/**
 * @author Oleg Orlov
 * @since 12 May 2015
 */
public class AtlasMessageComposer extends FrameLayout {
    private static final String TAG = AtlasMessageComposer.class.getSimpleName();
    private static final boolean debug = false;

    private EditText messageText;
    private View btnSend;
    private View btnUpload;

    private Listener listener;
    private Conversation conv;
    private LayerClient layerClient;

    private ArrayList<MenuItem> menuItems = new ArrayList<MenuItem>();

    // styles
    private int textColor;
    private float textSize;
    private Typeface typeFace;
    private int textStyle;
    private int mCharMode = -1;
    private OnClickListener mSwitchClickListener;

    //
    public AtlasMessageComposer(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        parseStyle(context, attrs, defStyle);
    }

    public AtlasMessageComposer(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AtlasMessageComposer(Context context) {
        super(context);
    }
    
    public void parseStyle(Context context, AttributeSet attrs, int defStyle) {
        TypedArray ta = context.getTheme().obtainStyledAttributes(attrs, R.styleable.AtlasMessageComposer, R.attr.AtlasMessageComposer, defStyle);
        this.textColor = ta.getColor(R.styleable.AtlasMessageComposer_textColor, context.getResources().getColor(R.color.atlas_text_black));
        //this.textSize  = ta.getDimension(R.styleable.AtlasMessageComposer_textSize, context.getResources().getDimension(R.dimen.atlas_text_size_general));
        this.textStyle = ta.getInt(R.styleable.AtlasMessageComposer_textStyle, Typeface.NORMAL);
        String typeFaceName = ta.getString(R.styleable.AtlasMessageComposer_textTypeface);
        this.typeFace  = typeFaceName != null ? Typeface.create(typeFaceName, textStyle) : null;
        ta.recycle();
    }
    
    /**
     * Initialization is required to engage MessageComposer with LayerClient and Conversation 
     * to send messages. 
     * <p>
     * If Conversation is not defined, "Send" action will not be able to send messages 
     * 
     * @param client - must be not null
     * @param conversation - could be null. Conversation could be provided later using {@link #setConversation(Conversation)}
     */
    public void init(LayerClient client, Conversation conversation) {
        if (client == null) throw new IllegalArgumentException("LayerClient cannot be null");
        
        this.layerClient = client;
        this.conv = conversation;
        
        LayoutInflater.from(getContext()).inflate(R.layout.atlas_message_composer, this);
        
        btnUpload = findViewById(R.id.atlas_message_composer_upload);
        btnUpload.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                final PopupWindow popupWindow = new PopupWindow(v.getContext());
                popupWindow.setWindowLayoutMode(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                LayoutInflater inflater = LayoutInflater.from(v.getContext());
                LinearLayout menu = (LinearLayout) inflater.inflate(R.layout.atlas_view_message_composer_menu, null);
                popupWindow.setContentView(menu);

                for (MenuItem item : menuItems) {
                    View itemConvert = inflater.inflate(R.layout.atlas_view_message_composer_menu_convert, menu, false);
                    TextView titleText = ((TextView) itemConvert.findViewById(R.id.altas_view_message_composer_convert_text));
                    titleText.setText(item.title);
                    itemConvert.setTag(item);
                    itemConvert.setOnClickListener(new OnClickListener() {
                        public void onClick(View v) {
                            popupWindow.dismiss();
                            MenuItem item = (MenuItem) v.getTag();
                            if (item.clickListener != null) {
                                item.clickListener.onClick(v);
                            }
                        }
                    });
                    menu.addView(itemConvert);
                }
                popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                popupWindow.setOutsideTouchable(true);
                int[] viewXYWindow = new int[2];
                v.getLocationInWindow(viewXYWindow);

                menu.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                int menuHeight = menu.getMeasuredHeight();
                popupWindow.showAtLocation(v, Gravity.NO_GRAVITY, viewXYWindow[0], viewXYWindow[1] - menuHeight);
            }
        });

        messageText = (EditText) findViewById(R.id.atlas_message_composer_text);
        messageText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (conv == null) return;
                try {
                    if (s.length() > 0) {
                        conv.send(LayerTypingIndicatorListener.TypingIndicator.STARTED);
                    } else {
                        conv.send(LayerTypingIndicatorListener.TypingIndicator.FINISHED);
                    }
                } catch (LayerException e) {
                    // `e.getType() == LayerException.Type.CONVERSATION_DELETED`
                }
            }
        });
        findViewById(R.id.atlas_message_composer_switch).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mSwitchClickListener != null) {
                    mSwitchClickListener.onClick(view);
                }
            }
        });
        Ion.with((ImageView) findViewById(R.id.atlas_message_composer_switch))
                .load("android.resource://" + getContext().getPackageName() + "/" + R.drawable.man);
        btnSend = findViewById(R.id.atlas_message_composer_send);
        btnSend.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {

                String text = messageText.getText().toString();
                if (text.trim().length() > 0) {
                    if (mCharMode == -1) {
                        sendToLayer(text);
                    } else {
                        // TODO query to server
                        Ion.with(getContext()).load("http://web1.tunnlr.com:12928/chat?q=" + text + "&mode=" + mCharMode)
                                .asString().setCallback(new FutureCallback<String>() {
                            @Override
                            public void onCompleted(Exception e, String result) {
                                if (e != null) {
                                    Toast.makeText(getContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
                                    return;
                                }
                                sendToLayer(result);
                            }
                        });

                    }
                }
            }
        });
        applyStyle();
    }

    private void sendToLayer(String text) {
        ArrayList<MessagePart> parts = new ArrayList<MessagePart>();
        String[] lines = text.split("\n+");
        for (String line : lines) {
            parts.add(layerClient.newMessagePart(line));
        }
        Message msg = layerClient.newMessage(parts);

        if (listener != null) {
            boolean proceed = listener.beforeSend(msg);
            if (!proceed) return;
        } else if (conv == null) {
            Log.e(TAG, "Cannot send message. Conversation is not set");
        }
        if (conv == null) return;

        conv.send(msg);
        messageText.setText("");
    }

    private void applyStyle() {
        //messageText.setTextSize(textSize);
        messageText.setTypeface(typeFace, textStyle);
        messageText.setTextColor(textColor);
    }

    public void registerMenuItem(String title, OnClickListener clickListener) {
        if (title == null) throw new NullPointerException("Item title must not be null");
        MenuItem item = new MenuItem();
        item.title = title;
        item.clickListener = clickListener;
        menuItems.add(item);
        btnUpload.setVisibility(View.VISIBLE);
    }
    
    public void setListener(Listener listener) {
        this.listener = listener;
    }
    
    public Conversation getConversation() {
        return conv;
    }

    public void setConversation(Conversation conv) {
        this.conv = conv;
    }

    public void setMode(int mode) {
        mCharMode = mode;
    }

    public interface Listener {
        boolean beforeSend(Message message);
    }
    
    private static class MenuItem {
        String title;
        OnClickListener clickListener;
    }

    public void setSwitchButtonResId(int resId) {
        Ion.with((ImageView) findViewById(R.id.atlas_message_composer_switch))
                .load("android.resource://" + getContext().getPackageName() + "/" + resId);
    }

    public void setOnClickSwitchButtonListener(OnClickListener listener) {
        mSwitchClickListener = listener;
    }
}
