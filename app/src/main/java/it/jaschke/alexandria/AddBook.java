package it.jaschke.alexandria;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import com.squareup.picasso.Picasso;

import it.jaschke.alexandria.data.AlexandriaContract;
import it.jaschke.alexandria.services.BookService;


public class AddBook extends Fragment implements LoaderManager.LoaderCallbacks<Cursor> {
    private static final String TAG = "INTENT_TO_SCAN_ACTIVITY";
    private EditText ean;
    private final int LOADER_ID = 1;
    private View rootView;
    private final String EAN_CONTENT = "eanContent";
    private static final String SCAN_FORMAT = "scanFormat";
    private static final String SCAN_CONTENTS = "scanContents";

    private String mScanFormat = "Format:";
    private String mScanContents = "Contents:";

    private int BOOK_EXECUTED = 1;

    public AddBook() {
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (ean != null) {
            outState.putString(EAN_CONTENT, ean.getText().toString());
        }
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        rootView = inflater.inflate(R.layout.fragment_add_book, container, false);
        ean = (EditText) rootView.findViewById(R.id.ean);

        ean.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                //no need
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                //no need
            }

            @Override
            public void afterTextChanged(Editable s) {
                String ean = s.toString();
                //catch isbn10 numbers
                if (ean.length() == 10 && !ean.startsWith("978")) {
                    ean = "978" + ean;
                }
                if (ean.length() < 13) {
                    clearFields();
                    return;
                }
                //Once we have an ISBN, start a book intent
                Intent bookIntent = new Intent(getActivity(), BookService.class);
                bookIntent.putExtra(BookService.EAN, ean);
                bookIntent.setAction(BookService.FETCH_BOOK);
                getActivity().startService(bookIntent);
                AddBook.this.restartLoader();
            }
        });

        rootView.findViewById(R.id.scan_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // This is the callback method that the system will invoke when your button is
                // clicked. You might do this by launching another app or by including the
                //functionality directly in this app.
                // Hint: Use a Try/Catch block to handle the Intent dispatch gracefully, if you
                // are using an external app.
                //when you're done, remove the toast below.

                IntentIntegrator integrator = IntentIntegrator.forSupportFragment(AddBook.this);

                integrator.setOrientationLocked(false)
                        .setPrompt("Please scan book ISBN BarCode")
                        .setBeepEnabled(false)
                        .initiateScan();

            }
        });

        rootView.findViewById(R.id.save_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                // check if the book is deleted or not
                if (BOOK_EXECUTED == 1) {
                    ean.setText("");
                    Toast.makeText(getActivity(), "Saved", Toast.LENGTH_SHORT).show();
                } else {
                    ean.setText("");
                }
            }
        });

        rootView.findViewById(R.id.delete_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent bookIntent = new Intent(getActivity(), BookService.class);

                // when book is deleted, BACK_BUTTON is pressed, service layout is recalled,
                // if "Cancel" button (R.id.delete_button) is pressed, app crashes
                if (!ean.getText().toString().equals("")) {
                    bookIntent.putExtra(BookService.EAN, ean.getText().toString());
                    bookIntent.setAction(BookService.DELETE_BOOK);
                    getActivity().startService(bookIntent);
                    ean.setText("");
                } else {
                    BOOK_EXECUTED = 0;
                    Toast.makeText(getActivity(), "This book has already been executed!", Toast.LENGTH_SHORT).show();
                }
            }
        });

        if (savedInstanceState != null) {
            ean.setText(savedInstanceState.getString(EAN_CONTENT));
            ean.setHint("");
        }

        return rootView;
    }

    // return and execute the result of scanning
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {

        IntentResult scanResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);

        // if data is not checked to be NOT NULL, when the camera preview has not recognized any codes and BACK BUTTON is pressed
        // app will crash due to data = null
        if (scanResult != null && data != null) {

            // check internet connection after receiving the barcode
            if (Utility.isNetworkAvailable(getActivity())) {

                // if users scan QR code, app will crash because the result is A WEB LINK INSTEAD OF A STRING OF NUMBERS
                // therefore, make sure users are scanning BARCODE prevents the app from crashing
                if (scanResult.getContents().substring(0, 4).equals("http")) {
                    openWebView(scanResult.getContents());
                } else {
                    // dont need to start FETCH_BOOK service again in here, when ean (EditText) is set with the barcode
                    // BookService is immediately called in the method afterTextChanged above
                    ean.setText(scanResult.getContents());
                }
            } else {
                Toast.makeText(getActivity(), "No internet connection", Toast.LENGTH_SHORT).show();
            }

        } else {
            super.onActivityResult(requestCode, resultCode, data);
            Log.v("AddBook", "No information");
        }
    }

    public void openWebView(String scanResult) {

        final String url = scanResult;

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setMessage("You are scanning an QR code, not a BarCode. Do you want to open this website ?")
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        Intent webIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));

                        if (webIntent.resolveActivity(getActivity().getPackageManager()) != null) {
                            startActivity(webIntent);
                        }
                    }
                })
                .setNegativeButton("No", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        dialogInterface.dismiss();
                    }
                });
        AlertDialog alertDialog = builder.create();
        alertDialog.show();
    }

    private void restartLoader() {
        getLoaderManager().restartLoader(LOADER_ID, null, this);
    }

    @Override
    public android.support.v4.content.Loader<Cursor> onCreateLoader(int id, Bundle args) {
        if (ean.getText().length() == 0) {
            return null;
        }
        String eanStr = ean.getText().toString();
        if (eanStr.length() == 10 && !eanStr.startsWith("978")) {
            eanStr = "978" + eanStr;
        }
        return new CursorLoader(
                getActivity(),
                AlexandriaContract.BookEntry.buildFullBookUri(Long.parseLong(eanStr)),
                null,
                null,
                null,
                null
        );
    }

    @Override
    public void onLoadFinished(android.support.v4.content.Loader<Cursor> loader, Cursor data) {
        if (!data.moveToFirst()) {
            return;
        }

        // make sure the app wont crash if data is NULL
        if (data != null) {
            String bookTitle = data.getString(data.getColumnIndex(AlexandriaContract.BookEntry.TITLE));
            ((TextView) rootView.findViewById(R.id.bookTitle)).setText(bookTitle);

            String bookSubTitle = data.getString(data.getColumnIndex(AlexandriaContract.BookEntry.SUBTITLE));
            ((TextView) rootView.findViewById(R.id.bookSubTitle)).setText(bookSubTitle);

            String authors = data.getString(data.getColumnIndex(AlexandriaContract.AuthorEntry.AUTHOR));

            // if authors returns NULL then app crashes when it executes these following lines
            if (authors != null) {
                String[] authorsArr = authors.split(",");
                ((TextView) rootView.findViewById(R.id.authors)).setLines(authorsArr.length);
                ((TextView) rootView.findViewById(R.id.authors)).setText(authors.replace(",", "\n"));
            } else {
                ((TextView) rootView.findViewById(R.id.authors)).setLines(1);
                ((TextView) rootView.findViewById(R.id.authors)).setText("Unknown author");
            }

            String imgUrl = data.getString(data.getColumnIndex(AlexandriaContract.BookEntry.IMAGE_URL));
            if (Patterns.WEB_URL.matcher(imgUrl).matches()) {

                Picasso.with(getActivity())
                        .load(imgUrl)
                        .into((ImageView) rootView.findViewById(R.id.bookCover));

                rootView.findViewById(R.id.bookCover).setVisibility(View.VISIBLE);

            }

            String categories = data.getString(data.getColumnIndex(AlexandriaContract.CategoryEntry.CATEGORY));
            ((TextView) rootView.findViewById(R.id.categories)).setText(categories);

            rootView.findViewById(R.id.save_button).setVisibility(View.VISIBLE);
            rootView.findViewById(R.id.delete_button).setVisibility(View.VISIBLE);
        } else {
            Toast.makeText(getActivity(), "Can not get data!", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onLoaderReset(android.support.v4.content.Loader<Cursor> loader) {

    }

    private void clearFields() {
        ((TextView) rootView.findViewById(R.id.bookTitle)).setText("");
        ((TextView) rootView.findViewById(R.id.bookSubTitle)).setText("");
        ((TextView) rootView.findViewById(R.id.authors)).setText("");
        ((TextView) rootView.findViewById(R.id.categories)).setText("");
        rootView.findViewById(R.id.bookCover).setVisibility(View.INVISIBLE);
        rootView.findViewById(R.id.save_button).setVisibility(View.INVISIBLE);
        rootView.findViewById(R.id.delete_button).setVisibility(View.INVISIBLE);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        activity.setTitle(R.string.scan);
    }
}
