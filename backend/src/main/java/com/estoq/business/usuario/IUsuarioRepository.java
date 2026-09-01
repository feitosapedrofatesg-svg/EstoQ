package com.estoq.business.usuario;

import com.estoq.core.repositories.IGenericRepository;
import java.util.List;

public interface IUsuarioRepository extends IGenericRepository<UsuarioModel> {

	List<UsuarioModel> findAllByAtivoTrue();
}