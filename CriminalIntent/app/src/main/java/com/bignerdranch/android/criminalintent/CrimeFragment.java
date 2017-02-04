package com.bignerdranch.android.criminalintent;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.provider.Settings;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.util.Log;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;

import com.google.android.gms.vision.Frame;


import com.google.android.gms.vision.Detector;
import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.face.Face;
import com.google.android.gms.vision.face.Landmark;
import com.google.android.gms.vision.face.FaceDetector;
import com.google.android.gms.vision.text.Text;


import android.util.SparseArray;
import android.widget.TextView;


import java.io.File;
import java.util.Date;
import java.util.UUID;

public class CrimeFragment extends Fragment {

    private static final String ARG_CRIME_ID = "crime_id";
    private static final String DIALOG_DATE = "DialogDate";

    private static final int REQUEST_DATE = 0;
    private static final int REQUEST_CONTACT = 1;
    private static final int REQUEST_PHOTO= 2;

    private Crime mCrime;
    private File mPhotoFile;
    private File altPhotoFile1;
    private File altPhotoFile2;
    private File altPhotoFile3;
    private EditText mTitleField;
    private Button mDateButton;
    private CheckBox mSolvedCheckbox;
    private Button mReportButton;
    private Button mSuspectButton;
    private ImageButton mPhotoButton;
    private ImageView mPhotoView;
    private ImageView altPhotoView1;
    private ImageView altPhotoView2;
    private ImageView altPhotoView3;
    private CheckBox detectionCheckbox;
    private TextView faceNum;
    private View v;

    //stores the ImageView at which the next picture should be place.  0 = mPhotoView, 1 = altPhotoView1...
    private int index = 0;

    public static CrimeFragment newInstance(UUID crimeId) {
        Bundle args = new Bundle();
        args.putSerializable(ARG_CRIME_ID, crimeId);

        CrimeFragment fragment = new CrimeFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {


        super.onCreate(savedInstanceState);
        UUID crimeId = (UUID) getArguments().getSerializable(ARG_CRIME_ID);
        mCrime = CrimeLab.get(getActivity()).getCrime(crimeId);
        mPhotoFile = CrimeLab.get(getActivity()).getPhotoFile(mCrime);
        altPhotoFile1 = CrimeLab.get(getActivity()).getAltPhotoFile(mCrime, 1);
        altPhotoFile2 = CrimeLab.get(getActivity()).getAltPhotoFile(mCrime, 2);
        altPhotoFile3 = CrimeLab.get(getActivity()).getAltPhotoFile(mCrime, 3);



    }

    @Override
    public void onPause() {
        super.onPause();

        CrimeLab.get(getActivity())
                .updateCrime(mCrime);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        v = inflater.inflate(R.layout.fragment_crime, container, false);

        mTitleField = (EditText) v.findViewById(R.id.crime_title);
        mTitleField.setText(mCrime.getTitle());
        mTitleField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                mCrime.setTitle(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });





        mDateButton = (Button) v.findViewById(R.id.crime_date);
        updateDate();
        mDateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FragmentManager manager = getFragmentManager();
                DatePickerFragment dialog = DatePickerFragment
                        .newInstance(mCrime.getDate());
                dialog.setTargetFragment(CrimeFragment.this, REQUEST_DATE);
                dialog.show(manager, DIALOG_DATE);
            }
        });

        mSolvedCheckbox = (CheckBox) v.findViewById(R.id.crime_solved);
        mSolvedCheckbox.setChecked(mCrime.isSolved());
        mSolvedCheckbox.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mCrime.setSolved(isChecked);
            }
        });

        mReportButton = (Button)v.findViewById(R.id.crime_report);
        mReportButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType("text/plain");
                i.putExtra(Intent.EXTRA_TEXT, getCrimeReport());
                i.putExtra(Intent.EXTRA_SUBJECT,
                        getString(R.string.crime_report_subject));
                i = Intent.createChooser(i, getString(R.string.send_report));

                startActivity(i);
            }
        });

        final Intent pickContact = new Intent(Intent.ACTION_PICK,
                ContactsContract.Contacts.CONTENT_URI);
        mSuspectButton = (Button)v.findViewById(R.id.crime_suspect);
        mSuspectButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startActivityForResult(pickContact, REQUEST_CONTACT);
            }
        });

        if (mCrime.getSuspect() != null) {
            mSuspectButton.setText(mCrime.getSuspect());
        }

        PackageManager packageManager = getActivity().getPackageManager();
        if (packageManager.resolveActivity(pickContact,
                PackageManager.MATCH_DEFAULT_ONLY) == null) {
            mSuspectButton.setEnabled(false);
        }

        mPhotoButton = (ImageButton) v.findViewById(R.id.crime_camera);
        final Intent captureImage = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

        boolean canTakePhoto = mPhotoFile != null &&
                captureImage.resolveActivity(packageManager) != null;
        mPhotoButton.setEnabled(canTakePhoto);

        Log.d("HI", "THE LENGTH:    "+getActivity().getApplicationContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES).length()%4096);


        if (canTakePhoto && mPhotoFile == null) {
            Uri uri = Uri.fromFile(mPhotoFile);
            captureImage.putExtra(MediaStore.EXTRA_OUTPUT, uri);
        }


        mPhotoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Uri uri = Uri.fromFile(mPhotoFile);
                if( index > 3){
                    index = 0;
                }else if(index == 1){
                    uri = Uri.fromFile(altPhotoFile1);
                }else if(index == 2){
                    uri = Uri.fromFile(altPhotoFile2);
                }else if(index == 3){
                    uri = Uri.fromFile(altPhotoFile3);
                }
                captureImage.putExtra(MediaStore.EXTRA_OUTPUT, uri);

                startActivityForResult(captureImage, REQUEST_PHOTO);
            }
        });

        mPhotoView = (ImageView) v.findViewById(R.id.crime_photo);
        altPhotoView1 = (ImageView) v.findViewById(R.id.imageView2);
        altPhotoView2 = (ImageView) v.findViewById(R.id.imageView3);
        altPhotoView3 = (ImageView) v.findViewById(R.id.imageView4);

        Bitmap bitmap;
        boolean loopBackTo0 = (index == 3);
        bitmap = PictureUtils.getScaledBitmap(
                altPhotoFile3.getPath(), getActivity());


        if( bitmap != null){
            altPhotoView3.setImageBitmap(bitmap);
            index = 3;
        }

        bitmap = PictureUtils.getScaledBitmap(
                altPhotoFile2.getPath(), getActivity());

        if( bitmap != null){
            altPhotoView2.setImageBitmap(bitmap);
            index = 2;
        }

        bitmap = PictureUtils.getScaledBitmap(
                altPhotoFile1.getPath(), getActivity());

        if( bitmap != null){
            altPhotoView1.setImageBitmap(bitmap);
            index = 1;
        }

        bitmap = PictureUtils.getScaledBitmap(
                mPhotoFile.getPath(), getActivity());

        if( bitmap != null){
            mPhotoView.setImageBitmap(bitmap);
            index = 0;
        }

        updatePhotoView();
        
        return v;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {

        if (resultCode != Activity.RESULT_OK) {
            return;
        }

        if (requestCode == REQUEST_DATE) {
            Date date = (Date) data
                    .getSerializableExtra(DatePickerFragment.EXTRA_DATE);
            mCrime.setDate(date);
            updateDate();
        } else if (requestCode == REQUEST_CONTACT && data != null) {
            Uri contactUri = data.getData();
            // Specify which fields you want your query to return
            // values for.
            String[] queryFields = new String[] {
                    ContactsContract.Contacts.DISPLAY_NAME,
            };
            // Perform your query - the contactUri is like a "where"
            // clause here
            ContentResolver resolver = getActivity().getContentResolver();
            Cursor c = resolver
                    .query(contactUri, queryFields, null, null, null);

            try {
                // Double-check that you actually got results
                if (c.getCount() == 0) {
                    return;
                }

                // Pull out the first column of the first row of data -
                // that is your suspect's name.
                c.moveToFirst();

                String suspect = c.getString(0);
                mCrime.setSuspect(suspect);
                mSuspectButton.setText(suspect);
            } finally {
                c.close();
            }
        } else if (requestCode == REQUEST_PHOTO) {
            updatePhotoView();



            detectionCheckbox = (CheckBox) v.findViewById(R.id.detectionEnable);
            faceNum = (TextView) v.findViewById(R.id.faceDetected);

            boolean checked = detectionCheckbox.isChecked();
            BitmapDrawable bd = (BitmapDrawable)altPhotoView3.getDrawable();
            if( index == 1){
                bd = (BitmapDrawable)mPhotoView.getDrawable();
            }else if( index == 2){
                bd = (BitmapDrawable)altPhotoView1.getDrawable();
            } else if( index == 3){
                bd = (BitmapDrawable)altPhotoView2.getDrawable();
            }
            FaceDetector detector = new FaceDetector.Builder(getContext())
                    .setTrackingEnabled(false)
                    .setLandmarkType(com.google.android.gms.vision.face.FaceDetector.ALL_LANDMARKS)
                    .build();

            System.out.println("Index is now: " + index);
            if (bd == null){
                System.out.println("But bd is null!!!!");
            }

            if (checked) {
                if(bd != null) {
                    Bitmap workingBitmap = bd.getBitmap();
                    Bitmap mutableBitmap = workingBitmap.copy(Bitmap.Config.ARGB_8888, true);

                    Frame frame = new Frame.Builder().setBitmap(mutableBitmap).build();

                    SparseArray<Face> faces = detector.detect(frame);

                    Canvas canvas = new Canvas(mutableBitmap);
                    Paint paint = new Paint();
                    paint.setColor(Color.BLUE);

                    faceNum.setText(faces.size() + " faces detected");

                    for (int i = 0; i < faces.size(); ++i) {
                        Face face = faces.valueAt(i);
                        for (Landmark landmark : face.getLandmarks()) {
                            int cx = (int) (landmark.getPosition().x * 1);
                            int cy = (int) (landmark.getPosition().y * 1);
                            //canvas.drawCircle(cx, cy, 10, paint);
                            canvas.drawRect(cx, cy, cx + 10, cy + 10, paint );
                        }
                    }
                    mPhotoView.setImageDrawable(new BitmapDrawable(getResources(), mutableBitmap));
                }
            } else {
                detector.release();
                faceNum.setText("");
            }
            index++;
        }
    }

    private void updateDate() {
        mDateButton.setText(mCrime.getDate().toString());
    }

    private String getCrimeReport() {
        String solvedString = null;
        if (mCrime.isSolved()) {
            solvedString = getString(R.string.crime_report_solved);
        } else {
            solvedString = getString(R.string.crime_report_unsolved);
        }
        String dateFormat = "EEE, MMM dd";
        String dateString = DateFormat.format(dateFormat, mCrime.getDate()).toString();
        String suspect = mCrime.getSuspect();
        if (suspect == null) {
            suspect = getString(R.string.crime_report_no_suspect);
        } else {
            suspect = getString(R.string.crime_report_suspect, suspect);
        }
        String report = getString(R.string.crime_report,
                mCrime.getTitle(), dateString, solvedString, suspect);
        return report;
    }

    private void updatePhotoView() {
        Bitmap bitmap;
        boolean loopBackTo0 = (index == 3);

        if (altPhotoFile3 == null || !altPhotoFile3.exists()) {
            altPhotoView3.setImageDrawable(null);
        }else{
            bitmap = PictureUtils.getScaledBitmap(
                    altPhotoFile3.getPath(), getActivity());
            altPhotoView3.setImageBitmap(bitmap);
        }

        if (altPhotoFile2 == null || !altPhotoFile2.exists()) {
            altPhotoView3.setImageDrawable(null);
        }else {
            bitmap = PictureUtils.getScaledBitmap(
                    altPhotoFile2.getPath(), getActivity());
            altPhotoView2.setImageBitmap(bitmap);
        }

        if (altPhotoFile1 == null || !altPhotoFile1.exists()) {
            mPhotoView.setImageDrawable(null);
        }else {
            bitmap = PictureUtils.getScaledBitmap(
                    altPhotoFile1.getPath(), getActivity());
            altPhotoView1.setImageBitmap(bitmap);
        }

        if (mPhotoFile == null || !mPhotoFile.exists()) {
            mPhotoView.setImageDrawable(null);
        } else {
            bitmap = PictureUtils.getScaledBitmap(
                    mPhotoFile.getPath(), getActivity());
            mPhotoView.setImageBitmap(bitmap);
        }

    }
}