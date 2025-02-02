package com.jerson.hcdc_portal.ui.fragments;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.jerson.hcdc_portal.PortalApp;
import com.jerson.hcdc_portal.databinding.FragmentGradesBinding;
import com.jerson.hcdc_portal.listener.DynamicListener;
import com.jerson.hcdc_portal.model.GradeModel;
import com.jerson.hcdc_portal.network.HttpClient;
import com.jerson.hcdc_portal.ui.adapter.GradeAdapter;
import com.jerson.hcdc_portal.util.BaseFragment;
import com.jerson.hcdc_portal.util.NetworkUtil;
import com.jerson.hcdc_portal.util.PreferenceManager;
import com.jerson.hcdc_portal.viewmodel.GradesViewModel;
import com.jerson.hcdc_portal.viewmodel.LoginViewModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;


public class GradesFragment extends BaseFragment<FragmentGradesBinding> {
    private static final String TAG = "GradesFragment";
    private FragmentGradesBinding binding;
    private List<GradeModel.Link> semGradeList = new ArrayList<>();
    private List<String> list = new ArrayList<>();
    private List<GradeModel> gradeList = new ArrayList<>();
    private GradeAdapter adapter;
    private GradesViewModel viewModel;
    private ArrayAdapter<String> arrayAdapter;

    private LoginViewModel loginViewModel;
    private PreferenceManager preferenceManager;
    private int selectedId = 0;
    private String selectedLink = "";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(GradesViewModel.class);
        loginViewModel = new ViewModelProvider(requireActivity()).get(LoginViewModel.class);
        preferenceManager = new PreferenceManager(requireActivity());
        loadGradeLink(linkListener);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding = getBinding();
        init();
    }

    void init() {
        if (!getBindingNull()) {

            // dropdown/spinner
            arrayAdapter = new ArrayAdapter<>(getActivity(), android.R.layout.simple_spinner_dropdown_item, list);
            arrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            binding.spinnerSem.setAdapter(arrayAdapter);

            observerErr();

            binding.spinnerSem.setFocusable(false);
            binding.spinnerSem.setOnItemClickListener((adapterView, view, i, l) -> {
                /*Log.d(TAG, "onItemClick: " + semGradeList.get(i).getLink() + "[" + semGradeList.get(i).getId() + "]");*/
                if (i != 0) {
                    binding.progressBar.setVisibility(View.VISIBLE);
                    binding.gradeLayout.setVisibility(View.GONE);
                    binding.gradeRecyclerView.setVisibility(View.GONE);
                    binding.refreshLayout.setEnabled(true);

                    selectedId = semGradeList.get(i).getId();
                    selectedLink = semGradeList.get(i).getLink();

                    loadGrade(semGradeList.get(i).getId(), object -> {
                        if (!object && !binding.refreshLayout.isRefreshing()) {
                            if (NetworkUtil.isConnected()) {
                                checkSession(object2 -> {
                                    if (object2) {
                                        getGrade(semGradeList.get(i).getId(), semGradeList.get(i).getLink());
                                    }
                                });
                            } else {
                                Toast.makeText(requireActivity(), "No internet connection.", Toast.LENGTH_SHORT).show();
                                showErr("No internet connection.");
                            }

                        }
                    });


                }
            });

            binding.refreshLayout.setOnRefreshListener(() -> {
                if (NetworkUtil.isConnected()) {
                    checkSession(object -> {
                        if (object) {
                            if (!binding.spinnerSem.getText().toString().equals(""))
                                getGrade(selectedId, selectedLink);
                            else
                                getLink();
                        }
                    });
                } else {
                    Toast.makeText(requireActivity(), "No internet connection.", Toast.LENGTH_SHORT).show();
                    binding.refreshLayout.setRefreshing(false);
                }

            });


            binding.gradeRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
            adapter = new GradeAdapter(gradeList, requireActivity());
            binding.gradeRecyclerView.setAdapter(adapter);

        }
    }

    void getLink() {
        viewModel.getLinks().observe(requireActivity(), data -> {
            if (data != null) {
                list.clear();
                semGradeList.clear();
                semGradeList.addAll(data);

                deleteGradeLink(object -> {
                    if (object) {
                        deleteAllGrade(isDeleted -> {
                            if (isDeleted) {
                                saveGradeLink(isSave -> {
                                    if (isSave) binding.refreshLayout.setRefreshing(false);
                                });
                            }
                        });
                    }
                });

                for (GradeModel.Link d : data) {
                    list.add(d.getText());
                }
                arrayAdapter.notifyDataSetChanged();
                binding.semSelectorLayout.setVisibility(View.VISIBLE);
                binding.progressBar.setVisibility(View.GONE);
            }

        });
    }

    void getGrade(int link_id, String link) {
        viewModel.gradeData(link).observe(requireActivity(), data -> {
            if (data != null) {
                gradeList.clear();
                gradeList.addAll(data);
                adapter.notifyDataSetChanged();

                deleteGrade(link_id, object -> {
                    if (object) {
                        saveGrade(link_id, data, object1 -> {
                            if (object1) {
                                binding.refreshLayout.setRefreshing(false);
                            }
                        });
                    }
                });

                String earn = gradeList.get(0).getEarnedUnits();
                String ave = gradeList.get(0).getAverage();
                binding.earnUnits.setText(earn);
                binding.weightedAve.setText(ave);

                binding.gradeLayout.setVisibility(View.VISIBLE);
                binding.semSelectorLayout.setVisibility(View.VISIBLE);
                binding.gradeRecyclerView.setVisibility(View.VISIBLE);

                binding.progressBar.setVisibility(View.GONE);

            }
        });
    }

    void checkSession(DynamicListener<Boolean> listener) {
        NetworkUtil.checkSession(object -> {
            if (object) {
                NetworkUtil.reLogin(logged->{
                    if(logged){
                        checkSession(listener);
                    }
                });

            } else listener.dynamicListener(true);

        });

    }

    void observerErr() {
        loginViewModel.getErr().observe(requireActivity(), err -> {
            showErr(err.getMessage());
        });

        viewModel.getErr().observe(requireActivity(), err -> {
            showErr(err.getMessage());
        });
    }

    /* database */
    void loadGradeLink(DynamicListener<Boolean> listener) {
        CompositeDisposable compositeDisposable = new CompositeDisposable();
        compositeDisposable.add(viewModel.loadGradeLink()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(data -> {
                    if (data.size() > 0) {
                        list.clear();
                        semGradeList.clear();
                        semGradeList.addAll(data);
                        for (GradeModel.Link d : data) {
                            list.add(d.getText());
                        }

                        listener.dynamicListener(true);
                    } else listener.dynamicListener(false);
                }, throwable -> {

                })
        );
    }

    DynamicListener<Boolean> linkListener = object -> {
        if (!object) {
            if (NetworkUtil.isConnected() && !binding.refreshLayout.isRefreshing()) {
                checkSession(object1 -> {
                    if (object1) {
                        getLink();
                    }
                });
            }

            if (!NetworkUtil.isConnected()) showErr("No internet connection.");

        } else {
            arrayAdapter.notifyDataSetChanged();
            binding.semSelectorLayout.setVisibility(View.VISIBLE);
            binding.progressBar.setVisibility(View.GONE);
        }
    };


    void saveGradeLink(DynamicListener<Boolean> listener) {
        CompositeDisposable compositeDisposable = new CompositeDisposable();
        compositeDisposable.add(viewModel.insertGradeLink(semGradeList)
                .subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
                .subscribe(() -> {
                            /*Log.w(TAG, "saveGradeLink Data saved: " + semGradeList.size());*/
                            listener.dynamicListener(true);
                        }, throwable -> {
                            listener.dynamicListener(false);
                        }
                ));

    }

    void deleteGradeLink(DynamicListener<Boolean> isDeleted) {
        CompositeDisposable compositeDisposable = new CompositeDisposable();
        compositeDisposable.add(viewModel.deleteGradeLink()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(() -> {
                    isDeleted.dynamicListener(true);
                }, throwable -> {
                    Log.e(TAG, "deleteGradeLink: ", throwable);
                    isDeleted.dynamicListener(false);
                }));
    }

    void loadGrade(int link_id, DynamicListener<Boolean> listener) {
        CompositeDisposable compositeDisposable = new CompositeDisposable();
        compositeDisposable.add(viewModel.loadGrade(link_id)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(data -> {
                    if (data.size() > 0) {
                        gradeList.clear();
                        gradeList.addAll(data);
                        adapter.notifyDataSetChanged();
                        String earn = gradeList.get(0).getEarnedUnits();
                        String ave = gradeList.get(0).getAverage();
                        binding.earnUnits.setText(earn);
                        binding.weightedAve.setText(ave);

                        binding.gradeLayout.setVisibility(View.VISIBLE);
                        binding.semSelectorLayout.setVisibility(View.VISIBLE);
                        binding.gradeRecyclerView.setVisibility(View.VISIBLE);

                        binding.progressBar.setVisibility(View.GONE);
                        binding.errLayout.setVisibility(View.GONE);

                    }
                    listener.dynamicListener(data.size() > 0);
                }, throwable -> Log.e(TAG, "loadGrade: ", throwable)));
    }

    void saveGrade(int link_id, List<GradeModel> list, DynamicListener<Boolean> listener) {
        for (int i = 0; i < list.size(); i++) {
            list.get(i).setLink_id(link_id);
        }
        CompositeDisposable compositeDisposable = new CompositeDisposable();
        compositeDisposable.add(viewModel.insertGrade(list)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(() -> {
                    listener.dynamicListener(true);
                }, throwable -> {
                    Log.e(TAG, "saveGrade: ", throwable);
                    listener.dynamicListener(false);
                })

        );
    }

    void deleteGrade(int link_id, DynamicListener<Boolean> listener) {
        CompositeDisposable compositeDisposable = new CompositeDisposable();
        compositeDisposable.add(viewModel.deleteGrade(link_id)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(() -> {
                    listener.dynamicListener(true);
                }, throwable -> {
                    Log.e(TAG, "deleteGrade: ", throwable);
                    listener.dynamicListener(false);
                })
        );
    }

    void deleteAllGrade(DynamicListener<Boolean> listener) {
        CompositeDisposable compositeDisposable = new CompositeDisposable();
        compositeDisposable.add(viewModel.deleteAllGradeData()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(() -> {
                    listener.dynamicListener(true);
                }, throwable -> {
                    listener.dynamicListener(false);
                })
        );
    }


    void showErr(String msg) {
        Random random = new Random();
        int n = random.nextInt(6);
        binding.progressBar.setVisibility(View.GONE);
        binding.gradeRecyclerView.setVisibility(View.GONE);
        binding.errLayout.setVisibility(View.VISIBLE);
        binding.errText.setText(msg);
        binding.errEmoji.setText(PortalApp.SAD_EMOJIS[n]);
    }

    @Override
    protected FragmentGradesBinding onCreateViewBinding(LayoutInflater layoutInflater, ViewGroup container) {
        return FragmentGradesBinding.inflate(layoutInflater, container, false);
    }

    public GradesFragment() {
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        HttpClient.getInstance().cancelRequest();
        ((ViewGroup) binding.refreshLayout.getParent()).removeView(binding.refreshLayout);
    }

}