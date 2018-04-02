package org.thoughtcrime.securesms.components;


import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.annimon.stream.Stream;
import com.annimon.stream.function.Predicate;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.attachments.Attachment;
import org.thoughtcrime.securesms.mms.AudioSlide;
import org.thoughtcrime.securesms.mms.DecryptableStreamUriLoader.DecryptableUri;
import org.thoughtcrime.securesms.mms.DocumentSlide;
import org.thoughtcrime.securesms.mms.GlideRequests;
import org.thoughtcrime.securesms.mms.ImageSlide;
import org.thoughtcrime.securesms.mms.Slide;
import org.thoughtcrime.securesms.mms.SlideDeck;
import org.thoughtcrime.securesms.mms.VideoSlide;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientModifiedListener;
import org.thoughtcrime.securesms.util.Util;

import java.util.List;

public class QuoteView extends LinearLayout implements RecipientModifiedListener {

  private static final String TAG = QuoteView.class.getSimpleName();

  private static final int MESSAGE_TYPE_PREVIEW  = 0;
  private static final int MESSAGE_TYPE_OUTGOING = 1;
  private static final int MESSAGE_TYPE_INCOMING = 2;

  private TextView  authorView;
  private TextView  bodyView;
  private ImageView quoteBarView;
  private ImageView attachmentView;
  private ImageView dismissView;

  private long      id;
  private Recipient author;
  private String    body;
  private ImageView mediaDescriptionIcon;
  private TextView  mediaDescriptionText;
  private SlideDeck attachments;
  private int       messageType;

  public QuoteView(Context context) {
    super(context);
    initialize(null);
  }

  public QuoteView(Context context, AttributeSet attrs) {
    super(context, attrs);
    initialize(attrs);
  }

  public QuoteView(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    initialize(attrs);
  }

  @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
  public QuoteView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
    super(context, attrs, defStyleAttr, defStyleRes);
    initialize(attrs);
  }

  private void initialize(@Nullable AttributeSet attrs) {
    inflate(getContext(), R.layout.quote_view, this);

    this.authorView           = findViewById(R.id.quote_author);
    this.bodyView             = findViewById(R.id.quote_text);
    this.quoteBarView         = findViewById(R.id.quote_bar);
    this.attachmentView       = findViewById(R.id.quote_attachment);
    this.dismissView          = findViewById(R.id.quote_dismiss);
    this.mediaDescriptionIcon = findViewById(R.id.media_icon);
    this.mediaDescriptionText = findViewById(R.id.media_name);

    if (attrs != null) {
      TypedArray typedArray  = getContext().getTheme().obtainStyledAttributes(attrs, R.styleable.QuoteView, 0, 0);
                 messageType = typedArray.getInt(R.styleable.QuoteView_message_type, 0);
      typedArray.recycle();

      dismissView.setVisibility(messageType == MESSAGE_TYPE_PREVIEW ? VISIBLE : GONE);
    }

    dismissView.setOnClickListener(view -> setVisibility(GONE));

    setBackgroundDrawable(getContext().getResources().getDrawable(R.drawable.quote_background));
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
  }

  public void setQuote(GlideRequests glideRequests, long id, @NonNull Recipient author, @Nullable String body, @NonNull SlideDeck attachments) {
    if (this.author != null) this.author.removeListener(this);

    this.id          = id;
    this.author      = author;
    this.body        = body;
    this.attachments = attachments;

    author.addListener(this);
    setQuoteAuthor(author);
    setQuoteText(body, attachments);
    setQuoteAttachment(glideRequests, attachments);
  }

  public void dismiss() {
    if (this.author != null) this.author.removeListener(this);

    this.id     = 0;
    this.author = null;
    this.body   = null;

    setVisibility(GONE);
  }

  @Override
  public void onModified(Recipient recipient) {
    Util.runOnMain(() -> {
      if (recipient == author) {
        setQuoteAuthor(recipient);
      }
    });
  }

  private void setQuoteAuthor(@NonNull Recipient author) {
    boolean outgoing = messageType != MESSAGE_TYPE_INCOMING;

    authorView.setText(author.toShortString());
    authorView.setTextColor(author.getColor().toQuoteTitleColor(getContext()));
    quoteBarView.setColorFilter(author.getColor().toQuoteBarColor(getContext(), outgoing), PorterDuff.Mode.SRC_IN);

    GradientDrawable background = (GradientDrawable) this.getBackground();
    background.setColor(author.getColor().toQuoteBackgroundColor(getContext(), outgoing));
    background.setStroke(getResources().getDimensionPixelSize(R.dimen.quote_outline_width),
                         author.getColor().toQuoteOutlineColor(getContext(), outgoing));
  }

  private void setQuoteText(@Nullable String body, @NonNull SlideDeck attachments) {
    if (attachments.containsMediaSlide()) {
      List<Slide> audioSlides    = Stream.of(attachments.getSlides()).filter(slide -> slide instanceof AudioSlide).limit(1).toList();
      List<Slide> documentSlides = Stream.of(attachments.getSlides()).filter(slide -> slide instanceof DocumentSlide).limit(1).toList();
      List<Slide> imageSlides    = Stream.of(attachments.getSlides()).filter(slide -> slide instanceof ImageSlide).limit(1).toList();
      List<Slide> videoSlides    = Stream.of(attachments.getSlides()).filter(slide -> slide instanceof VideoSlide).limit(1).toList();

      if (!imageSlides.isEmpty()) {
        if (TextUtils.isEmpty(body)) {
          bodyView.setVisibility(GONE);
          setAttachmentDescriptionVisibility(VISIBLE);
          mediaDescriptionIcon.setImageResource(R.drawable.ic_insert_photo_white_18dp);
          mediaDescriptionText.setText(R.string.QuoteView_photo);
        } else {
          bodyView.setText(body);
          bodyView.setVisibility(VISIBLE);
          setAttachmentDescriptionVisibility(GONE);
        }
      } else {
        setAttachmentDescriptionVisibility(VISIBLE);
        if (!audioSlides.isEmpty()) {
          mediaDescriptionIcon.setImageResource(R.drawable.ic_mic_white_24dp);
          mediaDescriptionText.setText(R.string.QuoteView_audio);
        } else if (!documentSlides.isEmpty()) {
          mediaDescriptionIcon.setImageResource(R.drawable.ic_insert_drive_file_white_24dp);
          mediaDescriptionText.setText(documentSlides.get(0).getFileName().or(getContext().getString(R.string.QuoteView_document)));
        } else if (!videoSlides.isEmpty()) {
          mediaDescriptionIcon.setImageResource(R.drawable.ic_play_circle_fill_white_48dp);
          mediaDescriptionText.setText(R.string.QuoteView_video);
        }
        if (TextUtils.isEmpty(body)) {
          bodyView.setVisibility(GONE);
        } else {
          bodyView.setVisibility(VISIBLE);
          bodyView.setText(body);
        }
      }
    } else {
      bodyView.setVisibility(VISIBLE);
      bodyView.setText(body == null ? "" : body);
      setAttachmentDescriptionVisibility(GONE);
    }
  }

  private void setQuoteAttachment(@NonNull GlideRequests glideRequests, @NonNull SlideDeck slideDeck) {
    List<Slide> imageVideoSlides = Stream.of(slideDeck.getSlides()).filter(s -> s.hasImage() || s.hasVideo()).limit(1).toList();

    if (!imageVideoSlides.isEmpty() && imageVideoSlides.get(0).getThumbnailUri() != null) {
      attachmentView.setVisibility(VISIBLE);
      dismissView.setBackgroundResource(R.drawable.circle_alpha);
      glideRequests.load(new DecryptableUri(imageVideoSlides.get(0).getThumbnailUri()))
                   .centerCrop()
                   .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                   .into(attachmentView);
    } else {
      attachmentView.setVisibility(GONE);
      dismissView.setBackgroundDrawable(null);
    }
  }

  private void setAttachmentDescriptionVisibility(int visibility) {
    mediaDescriptionIcon.setVisibility(visibility);
    mediaDescriptionText.setVisibility(visibility);
  }

  public long getQuoteId() {
    return id;
  }

  public Recipient getAuthor() {
    return author;
  }

  public String getBody() {
    return body;
  }

  public List<Attachment> getAttachments() {
    return attachments.asAttachments();
  }
}
