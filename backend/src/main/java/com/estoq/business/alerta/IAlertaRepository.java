package com.estoq.business.alerta;

import com.estoq.business.usuario.Perfil;
import com.estoq.core.repositories.IGenericRepository;

import java.util.List;

public interface IAlertaRepository extends IGenericRepository<AlertaModel> {

	List<AlertaModel> findAllByVisualizadoFalseAndAtivoTrueOrderByDataGeracaoDesc();

	List<AlertaModel> findAllByVisualizadoFalseAndAtivoTrue();

	List<AlertaModel> findAllByPerfilDestinoAndAtivoTrueOrderByDataGeracaoDesc(Perfil perfil);

	List<AlertaModel> findAllByAtivoTrueOrderByDataGeracaoDesc();

	long countByVisualizadoFalseAndAtivoTrue();
}